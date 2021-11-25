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

import static com.intellij.compiler.cache.ui.JpsLoaderNotifications.ATTENTION;

public final class JpsCacheStartupActivity implements StartupActivity.Background, Disposable {
  private static final Logger LOG = Logger.getInstance(JpsCacheStartupActivity.class);
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
    checkAutoBuildEnabled(project);
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

  private static void checkAutoBuildEnabled(@NotNull Project project) {
    //PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    //if (propertiesComponent.getBoolean(NOT_ASK_AGAIN)) {
    //  return;
    //}
    //
    //CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
    //if (!workspaceConfiguration.MAKE_PROJECT_ON_SAVE || !TrustedProjects.isTrusted(project)) {
    //  return;
    //}
    //
    //ATTENTION
    //  .createNotification(JpsCacheBundle.message("notification.title.automatic.project.build.enabled"),
    //                      JpsCacheBundle.message("notification.content.make.project.automatically.enabled.affect.caches"),
    //                      NotificationType.WARNING)
    //  .addAction(NotificationAction.createSimpleExpiring(
    //    JpsCacheBundle.message("action.NotificationAction.JpsCachesDummyProjectComponent.text.disable.property"), () -> {
    //      workspaceConfiguration.MAKE_PROJECT_ON_SAVE = false;
    //      BuildManager.getInstance().clearState(project);
    //    }))
    //  .addAction(NotificationAction.createSimpleExpiring(
    //    JpsCacheBundle.message("action.NotificationAction.JpsCachesDummyProjectComponent.text.dont.ask"), () -> {
    //      propertiesComponent.setValue(NOT_ASK_AGAIN, true);
    //    }))
    //  .notify(project);
  }

  public static boolean isLineEndingsConfiguredCorrectly() {
    return lineEndingsConfiguredCorrectly;
  }
}
