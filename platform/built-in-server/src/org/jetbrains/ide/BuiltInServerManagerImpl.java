package org.jetbrains.ide;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ShutDownTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuiltInServerManagerImpl extends BuiltInServerManager {
  private static final Logger LOG = Logger.getInstance(BuiltInServerManager.class);

  @NonNls
  public static final String PROPERTY_RPC_PORT = "rpc.port";
  private static final int PORTS_COUNT = 20;

  private volatile int detectedPortNumber = -1;
  private final AtomicBoolean started = new AtomicBoolean(false);

  @Nullable
  private BuiltInServer server;

  @Override
  public int getPort() {
    return detectedPortNumber == -1 ? getDefaultPort() : detectedPortNumber;
  }

  @Override
  public BuiltInServerManager waitForStart() {
    Future<?> serverStartFuture = startServerInPooledThread();
    if (serverStartFuture != null) {
      LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || !ApplicationManager.getApplication().isDispatchThread());
      try {
        serverStartFuture.get();
      }
      catch (InterruptedException ignored) {
      }
      catch (ExecutionException ignored) {
      }
    }
    return this;
  }

  private static int getDefaultPort() {
    if (System.getProperty(PROPERTY_RPC_PORT) == null) {
      // Default port will be occupied by main idea instance - define the custom default to avoid searching of free port
      return ApplicationManager.getApplication().isUnitTestMode() ? 64463 : 63342;
    }
    else {
      return Integer.parseInt(System.getProperty(PROPERTY_RPC_PORT));
    }
  }

  @Override
  public void initComponent() {
    startServerInPooledThread();
  }

  private Future<?> startServerInPooledThread() {
    if (!started.compareAndSet(false, true)) {
      return null;
    }

    return ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        int defaultPort = getDefaultPort();
        int workerCount = 1;
        // if user set special port number for some service (eg built-in web server), we should slightly increase worker count
        if (Runtime.getRuntime().availableProcessors() > 1) {
          for (CustomPortServerManager customPortServerManager : CustomPortServerManager.EP_NAME.getExtensions()) {
            if (customPortServerManager.getPort() != defaultPort) {
              workerCount = 2;
              break;
            }
          }
        }

        try {
          server = new BuiltInServer();
          detectedPortNumber = server.start(workerCount, defaultPort, PORTS_COUNT, true);
        }
        catch (Exception e) {
          LOG.info(e);
          String groupDisplayId = "Built-in Server";
          Notifications.Bus.register(groupDisplayId, NotificationDisplayType.STICKY_BALLOON);
          new Notification(groupDisplayId, "Internal HTTP server disabled",
                           "Cannot start internal HTTP server. Git integration, JavaScript debugger and LiveEdit may operate with errors. " +
                           "Please check your firewall settings and restart " + ApplicationNamesInfo.getInstance().getFullProductName(),
                           NotificationType.ERROR).notify(null);
          return;
        }

        if (detectedPortNumber == -1) {
          LOG.info("built-in server cannot be started, cannot bind to port");
          return;
        }

        LOG.info("built-in server started, port " + detectedPortNumber);

        Disposer.register(ApplicationManager.getApplication(), server);
        ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
          @Override
          public void run() {
            if (!Disposer.isDisposed(server)) {
              // something went wrong
              Disposer.dispose(server);
            }
          }
        });
      }
    });
  }

  @Override
  @Nullable
  public Disposable getServerDisposable() {
    return server;
  }
}