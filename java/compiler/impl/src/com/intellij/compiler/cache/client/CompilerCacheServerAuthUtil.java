package com.intellij.compiler.cache.client;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

import static com.intellij.compiler.cache.ui.CompilerCacheNotifications.ATTENTION;

public final class CompilerCacheServerAuthUtil {
  public static @NotNull Map<String, String> getRequestHeaders(@NotNull Project project) {
    return getRequestHeaders(project, false);
  }

  public static @NotNull Map<String, String> getRequestHeaders(@NotNull Project project, boolean force) {
    JpsServerAuthExtension authExtension = JpsServerAuthExtension.getInstance();
    if (authExtension == null) {
      String message = JavaCompilerBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work");
      ApplicationManager.getApplication().invokeLater(() -> {
        ATTENTION.createNotification(JavaCompilerBundle.message("notification.title.jps.caches.downloader"), message, NotificationType.WARNING).notify(project);
      });
      return Collections.emptyMap();
    }
    Map<String, String> authHeader = authExtension.getAuthHeader(force);
    if (authHeader == null) {
      String message = JavaCompilerBundle.message("internal.authentication.plugin.missing.token");
      ApplicationManager.getApplication().invokeLater(() -> {
        ATTENTION.createNotification(JavaCompilerBundle.message("notification.title.jps.caches.downloader"), message, NotificationType.WARNING).notify(project);
      });
      return Collections.emptyMap();
    }
    return authHeader;
  }
}
