// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer.liveReload;

import com.google.common.net.HttpHeaders;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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
import java.util.concurrent.TimeUnit;
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

  private final RequestedPagesState myState = new RequestedPagesState();

  private final @NotNull AsyncFileListener.ChangeApplier RELOAD_ALL = new AsyncFileListener.ChangeApplier() {
    @Override
    public void afterVfsChange() {
      myState.clear();
      ClientManager server = myServer;
      if (server != null) {
        server.send(-1, RELOAD_PAGE_MESSAGE.retainedDuplicate(), null);
      }
    }
  };

  public static WebServerPageConnectionService getInstance() {
    return ApplicationManager.getApplication().getService(WebServerPageConnectionService.class);
  }

  /**
   * @return suffix to add to requested file in response
   */
  public @Nullable CharSequence fileRequested(@NotNull FullHttpRequest request,
                                              @NotNull Supplier<? extends VirtualFile> fileSupplier) {
    boolean isReloadRequest = false;
    String uri = request.uri();
    if (uri != null && uri.contains(RELOAD_URL_PARAM)) {
      QueryStringDecoder decoder = new QueryStringDecoder(uri);
      isReloadRequest = decoder.parameters().containsKey(RELOAD_URL_PARAM);
    }

    if (!isReloadRequest && myState.isEmpty()) return null;

    VirtualFile file = fileSupplier.get();

    if (!isReloadRequest && file != null) {
      myState.resourceRequested(request, file);
      return null;
    }
    if (!isReloadRequest) return null;

    int clientId = myState.pageRequested(uri, file);

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

    if (myState.isRequestedFileWithoutReferrerModified(modifiedFiles)) {
      return RELOAD_ALL;
    }

    Set<CompletableFuture<WebSocketClient>> affectedClients = myState.collectAffectedPages(modifiedFiles);
    if (affectedClients.isEmpty()) return null;

    return new AsyncFileListener.ChangeApplier() {
      @Override
      public void afterVfsChange() {
        for (CompletableFuture<WebSocketClient> clientFuture : affectedClients) {
          clientFuture.thenAccept(client -> {
            client.send(RELOAD_PAGE_MESSAGE.retainedDuplicate());
          });
        }
      }
    };
  }

  private void clientConnected(@NotNull WebSocketClient client, int clientId) {
    myState.clientConnected(client, clientId);
  }

  private void clientDisconnected(@NotNull WebSocketClient client) {
    myState.clientDisconnected(client);
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

  private static class RequestedPagesState {
    private int myLastClientId = 0;
    private final @NotNull Set<VirtualFile> myRequestedFilesWithoutReferrer = new HashSet<>();
    private final @NotNull MultiMap<String, RequestedPage> myRequestedPages = new MultiMap<>();
    private @Nullable Disposable myListenerDisposable = null;

    public synchronized void clear() {
      for (RequestedPage requestedPage : myRequestedPages.values()) {
        requestedPage.myClient.cancel(false);
      }
      myRequestedPages.clear();
      cleanupIfEmpty();
    }

    public synchronized void resourceRequested(@NotNull FullHttpRequest request, @NotNull VirtualFile file) {
      String referer = request.headers().get(HttpHeaders.REFERER);
      boolean associatedPageFound = false;
      try {
        URI refererUri = URI.create(referer);
        String refererWithoutHost = refererUri.getPath() + "?" + refererUri.getQuery();
        Collection<RequestedPage> pages = myRequestedPages.get(refererWithoutHost);
        for (RequestedPage page : pages) {
          page.myFiles.add(file);
        }
        associatedPageFound = !pages.isEmpty();
      }
      catch (Throwable ignore) {
      }
      if (!associatedPageFound) {
        myRequestedFilesWithoutReferrer.add(file);
      }
    }

    public synchronized int pageRequested(@NotNull String uri, @NotNull VirtualFile file) {
      if (myRequestedPages.isEmpty()) {
        if (myListenerDisposable == null) {
          Disposable disposable =
            Disposer.newDisposable(ApplicationManager.getApplication(), WebServerFileContentListener.class.getSimpleName());
          VirtualFileManager.getInstance().addAsyncFileListener(new WebServerFileContentListener(), disposable);
          myListenerDisposable = disposable;
        }
        else {
          Logger.getInstance(RequestedPagesState.class).error("Listener already added");
        }
      }
      RequestedPage newPage = new RequestedPage(++myLastClientId, file);
      myRequestedPages.putValue(uri, newPage);
      JobScheduler.getScheduler().schedule(() -> stopWaitingForClient(uri, newPage), 30, TimeUnit.SECONDS);
      return myLastClientId;
    }

    private synchronized void stopWaitingForClient(@NotNull String uri, @NotNull RequestedPage page) {
      if (page.myClient.isDone()) return;
      page.myClient.cancel(false);
      myRequestedPages.remove(uri, page);
      cleanupIfEmpty();
      Logger.getInstance(RequestedPagesState.class).error("Timeout on waiting for client for " + uri);
    }

    public synchronized void clientConnected(@NotNull WebSocketClient client, int clientId) {
      RequestedPage requestedPage = ContainerUtil.find(myRequestedPages.values(), it -> it.myClientId == clientId);
      if (requestedPage != null) {
        requestedPage.myClient.complete(client);
      }
      else {
        // possible if 'clear' happens between page is registered and connected
        Logger.getInstance(WebServerPageConnectionService.class).info("Cannot find client for id = " + clientId);
      }
    }

    public synchronized void clientDisconnected(@NotNull WebSocketClient client) {
      String requestedPageKey = null;
      RequestedPage requestedPage = null;
      for (Map.Entry<String, Collection<RequestedPage>> entry : myRequestedPages.entrySet()) {
        for (RequestedPage page : entry.getValue()) {
          if (page.myClient.isDone() && page.myClient.getNow(null) == client) {
            requestedPageKey = entry.getKey();
            requestedPage = page;
            break;
          }
        }
      }

      if (requestedPageKey != null) {
        myRequestedPages.remove(requestedPageKey, requestedPage);
      }
      cleanupIfEmpty();
    }

    private void cleanupIfEmpty() {
      if (myRequestedPages.isEmpty()) {
        myRequestedFilesWithoutReferrer.clear();
        if (myListenerDisposable != null) {
          Disposer.dispose(Objects.requireNonNull(myListenerDisposable));
          myListenerDisposable = null;
        }
        else {
          Logger.getInstance(RequestedPagesState.class).error("Listener already disposed");
        }
      }
    }

    public synchronized boolean isRequestedFileWithoutReferrerModified(@NotNull List<VirtualFile> files) {
      for (VirtualFile file : files) {
        if (myRequestedFilesWithoutReferrer.contains(file)) return true;
      }
      return false;
    }

    public synchronized @NotNull Set<CompletableFuture<WebSocketClient>> collectAffectedPages(@NotNull List<VirtualFile> files) {
      HashSet<CompletableFuture<WebSocketClient>> result = new HashSet<>();
      for (VirtualFile modifiedFile : files) {
        for (RequestedPage requestedPage : myRequestedPages.values()) {
          if (requestedPage.myFiles.contains(modifiedFile)) {
            result.add(requestedPage.myClient);
          }
        }
      }
      return result;
    }

    public synchronized boolean isEmpty() {
      return myRequestedPages.isEmpty();
    }
  }

  private static class RequestedPage {
    private final int myClientId;
    private final @NotNull Set<VirtualFile> myFiles = new HashSet<>();
    private final @NotNull CompletableFuture<WebSocketClient> myClient = new CompletableFuture<>();

    private RequestedPage(int clientId, @NotNull VirtualFile requestedPageFile) {
      myClientId = clientId;
      myFiles.add(requestedPageFile);
    }
  }
}
