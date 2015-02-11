package org.jetbrains.io.webSocket;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.Disposer;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.NettyUtil;

import java.util.List;
import java.util.Map;

public abstract class WebSocketHandshakeHandler extends HttpRequestHandler implements WebSocketServerListener, ExceptionHandler {
  private static final Logger LOG = Logger.getInstance(WebSocketHandshakeHandler.class);

  static final AttributeKey<Client> CLIENT = AttributeKey.valueOf("WebSocketHandler.client");

  private final AtomicNotNullLazyValue<WebSocketServer> server = new AtomicNotNullLazyValue<WebSocketServer>() {
    @NotNull
    @Override
    protected WebSocketServer compute() {
      WebSocketServer result = new WebSocketServer(WebSocketHandshakeHandler.this, WebSocketHandshakeHandler.this);
      Disposable serverDisposable = BuiltInServerManager.getInstance().getServerDisposable();
      assert serverDisposable != null;
      Disposer.register(serverDisposable, result);
      serverCreated(result);
      return result;
    }
  };

  @Override
  public boolean isSupported(@NotNull FullHttpRequest request) {
    return request.method() == HttpMethod.GET &&
           "WebSocket".equalsIgnoreCase(request.headers().get(HttpHeaders.Names.UPGRADE)) &&
           request.uri().length() > 2;
  }

  protected void serverCreated(@NotNull WebSocketServer server) {
  }

  @Override
  public void exceptionCaught(Throwable e) {
    NettyUtil.log(e, LOG);
  }

  @Override
  public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) {
    handleWebSocketRequest(context, request, urlDecoder);
    return true;
  }

  private void handleWebSocketRequest(final ChannelHandlerContext context,
                                      FullHttpRequest request,
                                      final QueryStringDecoder uriDecoder) {
    WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory("ws://" + HttpHeaders.getHost(request) + uriDecoder.path(), null, false, NettyUtil.MAX_CONTENT_LENGTH);
    WebSocketServerHandshaker handshaker = factory.newHandshaker(request);
    if (handshaker == null) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
      return;
    }

    if (!context.channel().isOpen()) {
      return;
    }

    final Client client = new WebSocketClient(context.channel(), handshaker);
    context.attr(CLIENT).set(client);
    handshaker.handshake(context.channel(), request).addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          WebSocketServer webSocketServer = server.getValue();
          webSocketServer.addClient(client);
          MessageChannelHandler messageChannelHandler = new MessageChannelHandler(webSocketServer, getMessageServer());
          BuiltInServer.replaceDefaultHandler(context, messageChannelHandler);
          ChannelHandlerContext messageChannelHandlerContext = context.pipeline().context(messageChannelHandler);
          context.pipeline().addBefore(messageChannelHandlerContext.name(), "webSocketFrameAggregator", new WebSocketFrameAggregator(NettyUtil.MAX_CONTENT_LENGTH));
          messageChannelHandlerContext.attr(CLIENT).set(client);
          connected(client, uriDecoder.parameters());
        }
      }
    });
  }

  @NotNull
  protected abstract MessageServer getMessageServer();

  @Override
  public void connected(@NotNull Client client, Map<String, List<String>> parameters) {
  }
}