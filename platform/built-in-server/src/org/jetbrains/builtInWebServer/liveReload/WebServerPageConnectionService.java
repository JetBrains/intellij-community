// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer.liveReload;

import com.google.common.net.HttpHeaders;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.jsonRpc.Client;
import org.jetbrains.io.jsonRpc.ClientManager;
import org.jetbrains.io.jsonRpc.JsonRpcServer;
import org.jetbrains.io.jsonRpc.MessageServer;
import org.jetbrains.io.webSocket.WebSocketClient;
import org.jetbrains.io.webSocket.WebSocketHandshakeHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Provides support for automatic reloading of pages opened on built-in web server on related files modification.
 *
 * Implementation:
 *
 * <-- html page with {@link #RELOAD_URL_PARAM} is requested
 * --> response with modified html which opens WebSocket connection listening for reload message
 *
 * <-- script or other resource of html is requested
 * start listening for related file changes
 *
 * file is changed
 * --> reload associated pages by sending WebSocket message
 */
@Service(Service.Level.APP)
public final class WebServerPageConnectionService {

  public static final String RELOAD_URL_PARAM = "_ij_reload";
  private static final String RELOAD_WS_REQUEST = "reload";
  private static final String RELOAD_WS_URL_PREFIX = "jb-server-page";
  private static final String RELOAD_CLIENT_ID_URL_PARAMETER = "reloadServiceClientId";

  private final @NotNull ByteBuf RELOAD_PAGE_MESSAGE = Unpooled.copiedBuffer(RELOAD_WS_REQUEST, CharsetUtil.US_ASCII).asReadOnly();

  private @Nullable ClientManager myServer;
  private @Nullable JsonRpcServer myRpcServer;

  private final @NotNull AtomicInteger myClientsCount = new AtomicInteger(0);
  private volatile @Nullable Disposable myListenerDisposable;
  private final @NotNull AtomicInteger myTotalClientsCount = new AtomicInteger(0);
  private final @NotNull Set<VirtualFile> myRequestedFilesWithoutReferrer = ConcurrentHashMap.newKeySet();
  private final @NotNull Map<String, RequestedPage> myRequestedPages = new ConcurrentHashMap<>();

  private final @NotNull AsyncFileListener.ChangeApplier RELOAD_ALL = new AsyncFileListener.ChangeApplier() {
    @Override
    public void afterVfsChange() {
      myRequestedFilesWithoutReferrer.clear();
      Iterator<Map.Entry<String, RequestedPage>> iterator = myRequestedPages.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, RequestedPage> next = iterator.next();
        next.getValue().myClient.cancel(false);
        iterator.remove();
      }

      ClientManager server = myServer;
      if (server != null) {
        server.send(-1, RELOAD_PAGE_MESSAGE.retainedDuplicate(), null);
      }
    }
  };

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

    if (!isReloadRequest && myRequestedPages.isEmpty()) return null;

    VirtualFile file = fileSupplier.get();
    if (!isReloadRequest && file != null) {
      String referer = request.headers().get(HttpHeaders.REFERER);
      RequestedPage requestedPage = null;
      try {
        URI refererUri = URI.create(referer);
        String refererWithoutHost = refererUri.getPath() + "?" + refererUri.getQuery();
        requestedPage = myRequestedPages.get(refererWithoutHost);
      }
      catch (Throwable ignore) {}
      if (requestedPage != null) {
        requestedPage.myFiles.add(file);
      }
      else {
        myRequestedFilesWithoutReferrer.add(file);
      }
    }
    if (!isReloadRequest) return null;

    int clientId = myTotalClientsCount.incrementAndGet();
    myRequestedPages.put(uri, new RequestedPage(clientId, file));

    return new StringBuilder()
      .append("\n<script>\n")
      .append("new WebSocket('ws://' + window.location.host + '/").append(RELOAD_WS_URL_PREFIX)
      .append("?").append(RELOAD_CLIENT_ID_URL_PARAMETER).append("=").append(clientId)
      .append("').onmessage = function (msg) {\n")
      .append("  if (msg.data === '").append(RELOAD_WS_REQUEST).append("') {\n")
      .append("    window.location.reload();\n")
      .append("  }\n")
      .append("};\n")
      .append("</script>");
  }

  public @Nullable AsyncFileListener.ChangeApplier reloadRelatedClients(@NotNull List<VirtualFile> modifiedFiles) {
    ClientManager server = myServer;
    if (server == null) return null;

    Set<RequestedPage> affectedPages = new HashSet<>();
    for (VirtualFile modifiedFile : modifiedFiles) {
      if (myRequestedFilesWithoutReferrer.contains(modifiedFile)) {
        return RELOAD_ALL;
      }
      for (RequestedPage requestedPage : myRequestedPages.values()) {
        if (requestedPage.myFiles.contains(modifiedFile)) {
          affectedPages.add(requestedPage);
        }
      }
    }

    return new AsyncFileListener.ChangeApplier() {
      @Override
      public void afterVfsChange() {
        for (RequestedPage affectedPage : affectedPages) {
          affectedPage.myClient.thenAccept(client -> {
            client.send(RELOAD_PAGE_MESSAGE.retainedDuplicate());
          });
        }
      }
    };
  }

  private void clientConnected(@NotNull WebSocketClient client, int clientId) {
    if (myClientsCount.incrementAndGet() == 1) {
      Disposable disposable =
        Disposer.newDisposable(ApplicationManager.getApplication(), WebServerFileContentListener.class.getSimpleName());
      VirtualFileManager.getInstance().addAsyncFileListener(new WebServerFileContentListener(), disposable);
      myListenerDisposable = disposable;
    }

    for (RequestedPage requestedPage : myRequestedPages.values()) {
      if (requestedPage.myClientId == clientId) {
        requestedPage.myClient.complete(client);
        break;
      }
    }
  }

  private void clientDisconnected(@NotNull WebSocketClient client) {
    if (myClientsCount.decrementAndGet() == 0) {
      Disposer.dispose(Objects.requireNonNull(myListenerDisposable));
      myListenerDisposable = null;
    }
    String requestedPageKey = null;
    for (Map.Entry<String, RequestedPage> requestedPage : myRequestedPages.entrySet()) {
      if (requestedPage.getValue().myClient.isDone() && requestedPage.getValue().myClient.getNow(null) == client) {
        requestedPageKey = requestedPage.getKey();
        break;
      }
    }
    if (requestedPageKey != null) {
      myRequestedPages.remove(requestedPageKey);
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
      if (parameters == null || !(client instanceof WebSocketClient)) return;
      List<String> ids = parameters.get(RELOAD_CLIENT_ID_URL_PARAMETER);
      if (ids.size() != 1) return;
      int id = StringUtil.parseInt(ids.get(0), -1);
      if (id == -1) return;
      getInstance().clientConnected((WebSocketClient)client, id);
    }

    @Override
    public void disconnected(@NotNull Client client) {
      if (client instanceof WebSocketClient) {
        getInstance().clientDisconnected((WebSocketClient)client);
      }
    }
  }

  private static class RequestedPage {
    private final int myClientId;
    private final @NotNull Set<VirtualFile> myFiles = ConcurrentHashMap.newKeySet();
    private final @NotNull CompletableFuture<WebSocketClient> myClient = new CompletableFuture<>();

    private RequestedPage(int clientId, @NotNull VirtualFile requestedPageFile) {
      myClientId = clientId;
      myFiles.add(requestedPageFile);
    }
  }
}
