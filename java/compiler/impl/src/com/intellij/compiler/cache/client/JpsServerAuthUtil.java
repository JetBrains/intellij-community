package com.intellij.compiler.cache.client;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class JpsServerAuthUtil {
  public static @NotNull Map<String, String> getRequestHeaders() {
    JpsServerAuthExtension authExtension = JpsServerAuthExtension.getInstance();
    if (authExtension == null) {
      String message = JavaCompilerBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work.plugin");
      throw new RuntimeException(message);
    }
    Map<String, String> authHeader = authExtension.getAuthHeader();
    if (authHeader == null) {
      String message = JavaCompilerBundle.message("internal.authentication.plugin.missing.token");
      throw new RuntimeException(message);
    }
    return authHeader;
  }
}
