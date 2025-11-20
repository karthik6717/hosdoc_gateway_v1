package com.kalex.hosdoc_gateway;

import java.util.Arrays;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtValidationFilter extends AbstractGatewayFilterFactory<JwtValidationFilter.Config> {

    private final WebClient webClient;

    public JwtValidationFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        // Configure to call hosdoc_auth service for JWT validation
        // Use environment variable or default to localhost for local development
        String authServiceUrl = System.getenv("AUTH_SERVICE_URL");
        if (authServiceUrl == null || authServiceUrl.isEmpty()) {
            authServiceUrl = "http://localhost:8081";
        }
        this.webClient = webClientBuilder.baseUrl(authServiceUrl + "/api/auth").build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String requestPath = exchange.getRequest().getPath().toString();
            
            // Skip JWT validation for public endpoints
            if (isExcludedPath(requestPath)) {
                return chain.filter(exchange);
            }
            
            // Extract token from Authorization header
            String token = extractToken(exchange);
            if (token == null) {
                return unauthorized(exchange);
            }
            
            // Validate token by calling auth service
            return webClient.post()
                    .uri("/validate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity()
                    .flatMap(response -> chain.filter(exchange))
                    .onErrorResume(e -> unauthorized(exchange));
        };
    }

    /**
     * Check if the path should be excluded from JWT validation
     * Public endpoints that don't require authentication
     */
    private boolean isExcludedPath(String path) {
        List<String> exclude = Arrays.asList(
                "/api/auth/login",
                "/api/auth/register",
                "/api/v1/specialties",
                "/api/v1/doctors",
                "/swagger-ui",
                "/v3/api-docs",
                "/swagger-resources"
        );
        return exclude.stream().anyMatch(path::startsWith);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * Return 401 Unauthorized response
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // Configuration properties can be added here if needed
        //comment add to test image file
    }
}

