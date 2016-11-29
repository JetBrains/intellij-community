package org.jetbrains.io.webSocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.jsonRpc.Client;
import org.jetbrains.io.jsonRpc.ClientManager;
import org.jetbrains.io.jsonRpc.ClientManagerKt;
import org.jetbrains.io.jsonRpc.MessageServer;

import static com.intellij.util.io.NettyKt.readUtf8;

@ChannelHandler.Sharable
final class MessageChannelHandler extends WebSocketProtocolHandler {
  private final ClientManager clientManager;
  private final MessageServer messageServer;

  MessageChannelHandler(@NotNull ClientManager clientManager, @NotNull MessageServer messageServer) {
    this.clientManager = clientManager;
    this.messageServer = messageServer;
  }

  @Override
  protected void closeFrameReceived(@NotNull Channel channel, @NotNull CloseWebSocketFrame message) {
    WebSocketClient client = (WebSocketClient)channel.attr(ClientManagerKt.getCLIENT()).get();
    if (client == null) {
      super.closeFrameReceived(channel, message);
    }
    else {
      try {
        clientManager.disconnectClient(channel, client, false);
      }
      finally {
        client.disconnect(message);
      }
    }
  }

  @Override
  protected void textFrameReceived(@NotNull Channel channel, @NotNull TextWebSocketFrame message) {
    WebSocketClient client = (WebSocketClient)channel.attr(ClientManagerKt.getCLIENT()).get();
    CharSequence chars;
    try {
      chars = readUtf8(message.content());
    }
    catch (Throwable e) {
      try {
        message.release();
      }
      finally {
        clientManager.getExceptionHandler().exceptionCaught(e);
      }
      return;
    }

    try {
      messageServer.messageReceived(client, chars);
    }
    catch (Throwable e) {
      clientManager.getExceptionHandler().exceptionCaught(e);
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    Client client = context.channel().attr(ClientManagerKt.getCLIENT()).get();
    // if null, so, has already been explicitly removed
    if (client != null) {
      clientManager.disconnectClient(context.channel(), client, false);
    }
  }

  @Override
  public void exceptionCaught(@NotNull ChannelHandlerContext context, @NotNull Throwable cause) {
    try {
      clientManager.getExceptionHandler().exceptionCaught(cause);
    }
    finally {
      super.exceptionCaught(context, cause);
    }
  }
}
