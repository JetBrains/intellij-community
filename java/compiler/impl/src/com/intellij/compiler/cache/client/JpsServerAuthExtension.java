package com.intellij.compiler.cache.client;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.compiler.cache.ui.JpsLoaderNotifications.ATTENTION;
import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

/**
 * Extension point which provides authentication data for requests to the JPS cache server
 */
public interface JpsServerAuthExtension {
  Logger LOG = Logger.getInstance(JpsServerAuthExtension.class);
  Key<Boolean> NOTIFICATION_SHOWN_KEY = Key.create("AUTH_NOTIFICATION_SHOWN");
  ExtensionPointName<JpsServerAuthExtension> EP_NAME = ExtensionPointName.create("com.intellij.jpsServerAuthExtension");

  /**
   * This method should check if the user was authenticated, if not it should do any needed actions to provide
   * auth token for further requests. This method will be called outside of the EDT and should be asynchronous.
   * If the user was authenticated the callback should be invoked.
   *
   * @param presentableReason reason for the token request
   * @param parentDisposable controls the lifetime of the authentication
   * @param onAuthCompleted callback on authentication complete, if token already exists it also should be invoked
   */
   void checkAuthenticated(@NotNull String presentableReason, @NotNull Disposable parentDisposable, @NotNull Runnable onAuthCompleted);

  /**
   * The method provides HTTP authentication headers for the requests to the server.
   * It will be called in the background thread. The assertion that thread isn't EDT can
   * be added to the implementation. If it's not possible to get the authentication headers,
   * empty map or `null` can be return.
   * @return
   */
  Map<String, String> getAuthHeader();

  @Nullable
  static JpsServerAuthExtension getInstance() {
    return EP_NAME.extensions().findFirst().orElse(null);
  }

  static void checkAuthenticatedInBackgroundThread(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull Runnable onAuthCompleted) {
    Disposable disposable = Disposer.newDisposable();
    Disposer.register(parentDisposable, disposable);
    JpsServerAuthExtension authExtension = getInstance();
    if (authExtension == null) {
      Boolean userData = project.getUserData(NOTIFICATION_SHOWN_KEY);
      if (userData == null) {
        project.putUserData(NOTIFICATION_SHOWN_KEY, Boolean.TRUE);
        ApplicationManager.getApplication().invokeLater(() -> {
          ATTENTION
            .createNotification(JavaCompilerBundle.message("notification.title.jps.caches.downloader"),
                                JavaCompilerBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work"),
                                NotificationType.WARNING)
            .setListener(NotificationListener.URL_OPENING_LISTENER)
            .notify(project);
        });
      }
      LOG.warn("JetBrains Internal Authentication plugin is required for the correct work. Please enable it.");
      return;
    }
    INSTANCE.execute(() -> {
      authExtension.checkAuthenticated("Jps Caches Downloader", disposable, () -> {
        Disposer.dispose(disposable);
        onAuthCompleted.run();
      });
    });
  }
}
