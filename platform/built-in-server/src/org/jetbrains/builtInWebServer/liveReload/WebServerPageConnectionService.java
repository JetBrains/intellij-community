// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer.liveReload;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.NettyKt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.BuiltInServerOptions;
import org.jetbrains.io.jsonRpc.Client;
import org.jetbrains.io.jsonRpc.ClientManager;
import org.jetbrains.io.jsonRpc.JsonRpcServer;
import org.jetbrains.io.jsonRpc.MessageServer;
import org.jetbrains.io.webSocket.WebSocketHandshakeHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service(Service.Level.APP)
public final class WebServerPageConnectionService {

  public static final String RELOAD_URL_PARAM = "_ij_reload";
  private static final String RELOAD_WS_REQUEST = "reload";
  private static final String RELOAD_WS_URL_PREFIX = "jb-server-page";

  private final @NotNull ByteBuf RELOAD_PAGE_MESSAGE = Unpooled.copiedBuffer(RELOAD_WS_REQUEST, CharsetUtil.US_ASCII).asReadOnly();

  private @Nullable ClientManager myServer;
  private @Nullable JsonRpcServer myRpcServer;

  private final @NotNull AtomicInteger ourListenersCount = new AtomicInteger(0);
  private volatile @Nullable Disposable ourListenerDisposable;
  private final @NotNull Set<VirtualFile> myRequestedFiles = ConcurrentHashMap.newKeySet();

  public static WebServerPageConnectionService getInstance() {
    return ApplicationManager.getApplication().getService(WebServerPageConnectionService.class);
  }

  public @Nullable CharSequence fileRequested(@NotNull FullHttpRequest request,
                                              @NotNull Supplier<? extends VirtualFile> fileSupplier) {
    boolean isReloadRequest = false;
    String uri = request.uri();
    if (uri != null && uri.contains(RELOAD_URL_PARAM)) {
      QueryStringDecoder decoder = new QueryStringDecoder(uri);
      isReloadRequest = decoder.parameters().containsKey(RELOAD_URL_PARAM);
    }

    if (isReloadRequest || !myRequestedFiles.isEmpty()) {
      VirtualFile file = fileSupplier.get();
      if (file != null) {
        myRequestedFiles.add(file);
      }
    }
    if (!isReloadRequest) return null;

    String host = NettyKt.getHost(request);
    if (host == null) host = "localhost:" + BuiltInServerOptions.getInstance().getEffectiveBuiltInServerPort();
    return new StringBuilder()
      .append("\n<script>\n")
      .append("new WebSocket('ws://").append(host).append("/").append(RELOAD_WS_URL_PREFIX).append("').onmessage = function (msg) {\n")
      .append("  if (msg.data === '").append(RELOAD_WS_REQUEST).append("') {\n")
      .append("    window.location.reload();\n")
      .append("  }\n")
      .append("};\n")
      .append("</script>");
  }

  public boolean isFileRequested(@NotNull VirtualFile file) {
    return myRequestedFiles.contains(file);
  }

  public void reloadAll() {
    ClientManager server = myServer;
    if (server != null) {
      server.send(-1, RELOAD_PAGE_MESSAGE.retainedDuplicate(), null);
    }
  }

  private void clientConnected() {
    if (ourListenersCount.incrementAndGet() == 1) {
      Disposable disposable =
        Disposer.newDisposable(ApplicationManager.getApplication(), WebServerFileContentListener.class.getSimpleName());
      VirtualFileManager.getInstance().addAsyncFileListener(new WebServerFileContentListener(), disposable);
      ourListenerDisposable = disposable;
    }
  }

  private void clientDisconnected() {
    if (ourListenersCount.decrementAndGet() == 0) {
      Disposer.dispose(Objects.requireNonNull(ourListenerDisposable));
      ourListenerDisposable = null;
    }
  }

  static final class WebServerPageRequestHandler extends WebSocketHandshakeHandler {

    @Override
    protected void serverCreated(@NotNull ClientManager server) {
      WebServerPageConnectionService instance = getInstance();
      instance.myServer = server;
      instance.myRpcServer = new JsonRpcServer(server);
    }

    @Override
    public boolean isSupported(@NotNull FullHttpRequest request) {
      return super.isSupported(request) && checkPrefix(request.uri(), RELOAD_WS_URL_PREFIX);
    }

    @Override
    protected @NotNull MessageServer getMessageServer() {
      //noinspection ConstantConditions
      return getInstance().myRpcServer;
    }

    @Override
    public void connected(@NotNull Client client, @Nullable Map<String, List<String>> parameters) {
      getInstance().clientConnected();
    }

    @Override
    public void disconnected(@NotNull Client client) {
      getInstance().clientDisconnected();
    }
  }
}
