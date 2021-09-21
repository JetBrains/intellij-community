package com.intellij.compiler.cache;

import com.intellij.compiler.cache.client.JpsServerAuthExtension;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

//import static com.intellij.jps.cache.JpsCachesPluginUtil.INTELLIJ_REPO_NAME;
//import static com.intellij.jps.cache.ui.JpsLoaderNotifications.ATTENTION;

public final class JpsCacheStartupActivity implements StartupActivity.Background {
  private static final String NOT_ASK_AGAIN = "JpsCaches.NOT_ASK_AGAIN";
  private static boolean lineEndingsConfiguredCorrectly = true;

  @Override
  public void runActivity(@NotNull Project project) {
    if (Registry.is("compiler.process.use.portable.caches") && GitRepositoryUtil.isIntelliJRepository(project)) {
      JpsServerAuthExtension.checkAuthenticatedInBackgroundThread(project, project, () -> {
        System.out.println("All Ok");
      });
      checkWindowsCRLF(project);
      checkAutoBuildEnabled(project);
    }
  }

  private static void checkWindowsCRLF(@NotNull Project project) {
    //if (!SystemInfo.isWindows) return;
    //GitRepository intellijRepository = GitRepositoryUtil.getRepositoryByName(project, INTELLIJ_REPO_NAME);
    //if (intellijRepository == null) return;
    //if (!GitRepositoryUtil.isAutoCrlfSetRight(intellijRepository)) {
    //  lineEndingsConfiguredCorrectly = false;
    //  ATTENTION
    //    .createNotification(JpsCacheBundle.message("notification.title.git.crlf.config"),
    //                        JpsCacheBundle.message("notification.content.git.crlf.config"),
    //                        NotificationType.WARNING)
    //    .addAction(NotificationAction.createSimple(JpsCacheBundle.message("notification.action.git.crlf.config"),
    //                                               () -> BrowserLauncher.getInstance().open("https://confluence.jetbrains.com/pages/viewpage.action?title=Git+Repository&spaceKey=IDEA")))
    //    .notify(project);
    //}
  }

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
