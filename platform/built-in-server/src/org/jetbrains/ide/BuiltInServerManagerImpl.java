// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide;

import com.intellij.idea.StartupUtil;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.net.NetUtils;
import io.netty.channel.oio.OioEventLoopGroup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.BuiltInServerOptions;
import org.jetbrains.builtInWebServer.BuiltInWebServerKt;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.SubServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuiltInServerManagerImpl extends BuiltInServerManager implements ApplicationComponent {
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
      catch (InterruptedException | ExecutionException ignored) {
      }
    }
    return this;
  }

  private static int getDefaultPort() {
    if (System.getProperty(PROPERTY_RPC_PORT) == null) {
      // Default port will be occupied by main idea instance - define the custom default to avoid searching of free port
      return ApplicationManager.getApplication().isUnitTestMode() ? 64463 : BuiltInServerOptions.DEFAULT_PORT;
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

    return ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        BuiltInServer mainServer = StartupUtil.getServer();
        if (mainServer == null || mainServer.getEventLoopGroup() instanceof OioEventLoopGroup) {
          server = BuiltInServer.start(1, getDefaultPort(), PORTS_COUNT, false, null);
        }
        else {
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
    });
  }

  @Override
  @Nullable
  public Disposable getServerDisposable() {
    return server;
  }

  @Override
  public boolean isOnBuiltInWebServer(@Nullable Url url) {
    return url != null && !StringUtil.isEmpty(url.getAuthority()) && isOnBuiltInWebServerByAuthority(url.getAuthority());
  }

  @Override
  public Url addAuthToken(@NotNull Url url) {
    if (url.getParameters() != null) {
      // built-in server url contains query only if token specified
      return url;
    }
    return Urls.newUrl(Objects.requireNonNull(url.getScheme()), Objects.requireNonNull(url.getAuthority()), url.getPath(),
                       Collections.singletonMap(BuiltInWebServerKt.TOKEN_PARAM_NAME, BuiltInWebServerKt.acquireToken()));
  }

  @Override
  public void configureRequestToWebServer(@NotNull URLConnection connection) {
    connection.setRequestProperty(BuiltInWebServerKt.TOKEN_HEADER_NAME, BuiltInWebServerKt.acquireToken());
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

  public static boolean isOnBuiltInWebServerByAuthority(@NotNull String authority) {
    int portIndex = authority.indexOf(':');
    if (portIndex < 0 || portIndex == authority.length() - 1) {
      return false;
    }

    int port = StringUtil.parseInt(authority.substring(portIndex + 1), -1);
    if (port == -1) {
      return false;
    }

    BuiltInServerOptions options = BuiltInServerOptions.getInstance();
    int idePort = BuiltInServerManager.getInstance().getPort();
    if (options.builtInServerPort != port && idePort != port) {
      return false;
    }

    String host = authority.substring(0, portIndex);
    if (NetUtils.isLocalhost(host)) {
      return true;
    }

    try {
      InetAddress inetAddress = InetAddress.getByName(host);
      return inetAddress.isLoopbackAddress() ||
             inetAddress.isAnyLocalAddress() ||
             (options.builtInServerAvailableExternally && idePort != port && NetworkInterface.getByInetAddress(inetAddress) != null);
    }
    catch (IOException e) {
      return false;
    }
  }
}