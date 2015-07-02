package org.jetbrains.ide;

import com.intellij.idea.StartupUtil;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.SubServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuiltInServerManagerImpl extends BuiltInServerManager {
  private static final Logger LOG = Logger.getInstance(BuiltInServerManager.class);

  public static final NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = new NotNullLazyValue<NotificationGroup>() {
    @NotNull
    @Override
    protected NotificationGroup compute() {
      return new NotificationGroup("Built-in Server", NotificationDisplayType.STICKY_BALLOON, true);
    }
  };

  @NonNls
  public static final String PROPERTY_RPC_PORT = "rpc.port";
  private static final int PORTS_COUNT = 20;

  private final AtomicBoolean started = new AtomicBoolean(false);

  @Nullable
  private BuiltInServer server;

  @Override
  public int getPort() {
    return server == null ? getDefaultPort() : server.getPort();
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
        try {
          BuiltInServer mainServer = StartupUtil.getServer();
          if (mainServer == null && ApplicationManager.getApplication().isUnitTestMode()) {
            server = BuiltInServer.start(1, getDefaultPort(), PORTS_COUNT, false, null);
          }
          else {
            LOG.assertTrue(mainServer != null);
            server = BuiltInServer.start(mainServer.getEventLoopGroup(), false, getDefaultPort(), PORTS_COUNT, true, null);
          }
          bindCustomPorts(server);
        }
        catch (Throwable e) {
          LOG.info(e);
          NOTIFICATION_GROUP.getValue().createNotification("Cannot start internal HTTP server. Git integration, JavaScript debugger and LiveEdit may operate with errors. " +
                                                           "Please check your firewall settings and restart " + ApplicationNamesInfo.getInstance().getFullProductName(),
                                                           NotificationType.ERROR).notify(null);
          return;
        }

        LOG.info("built-in server started, port " + server.getPort());

        Disposer.register(ApplicationManager.getApplication(), server);
      }
    });
  }

  @Override
  @Nullable
  public Disposable getServerDisposable() {
    return server;
  }

  private static void bindCustomPorts(@NotNull BuiltInServer server) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    for (CustomPortServerManager customPortServerManager : CustomPortServerManager.EP_NAME.getExtensions()) {
      try {
        new SubServer(customPortServerManager, server).bind(customPortServerManager.getPort());
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }
}