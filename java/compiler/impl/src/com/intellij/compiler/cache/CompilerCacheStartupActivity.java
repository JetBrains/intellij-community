package com.intellij.compiler.cache;

import com.intellij.compiler.cache.client.JpsServerAuthExtension;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import static com.intellij.compiler.cache.ui.CompilerCacheNotifications.ATTENTION;

public final class CompilerCacheStartupActivity implements StartupActivity.Background, Disposable {
  private static final Logger LOG = Logger.getInstance(CompilerCacheStartupActivity.class);
  private static final String NOT_ASK_AGAIN = "JpsCaches.NOT_ASK_AGAIN";
  private static boolean lineEndingsConfiguredCorrectly = true;

  @Override
  public void runActivity(@NotNull Project project) {
    if (!Registry.is("compiler.process.use.portable.caches")) {
      LOG.debug("JPS Caches registry key is not enabled");
      return;
    }
    if (!GitRepositoryUtil.isIntelliJRepository(project)) {
      LOG.debug("Not an Intellij project, JPS Caches will not be available");
      return;
    }
    JpsServerAuthExtension.checkAuthenticatedInBackgroundThread(this, project, () -> {
      LOG.info("User authentication for JPS Cache download complete successfully");
    });
    checkWindowsCRLF(project);
  }

  private static void checkWindowsCRLF(@NotNull Project project) {
    if (!SystemInfo.isWindows) return;
    if (!GitRepositoryUtil.isAutoCrlfSetRight(project)) {
      lineEndingsConfiguredCorrectly = false;
      ATTENTION
        .createNotification(JavaCompilerBundle.message("notification.title.git.crlf.config"),
                            JavaCompilerBundle.message("notification.content.git.crlf.config"),
                            NotificationType.WARNING)
        .addAction(NotificationAction.createSimple(JavaCompilerBundle.message("notification.action.git.crlf.config"),
                                                   () -> BrowserLauncher.getInstance().open("https://confluence.jetbrains.com/pages/viewpage.action?title=Git+Repository&spaceKey=IDEA")))
        .notify(project);
    }
  }

  @Override
  public void dispose() { }

  public static boolean isLineEndingsConfiguredCorrectly() {
    return lineEndingsConfiguredCorrectly;
  }
}
