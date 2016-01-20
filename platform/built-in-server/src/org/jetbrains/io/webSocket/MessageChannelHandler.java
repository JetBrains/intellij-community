package org.jetbrains.io.webSocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.ChannelBufferToString;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;
import org.jetbrains.io.jsonRpc.Client;
import org.jetbrains.io.jsonRpc.ClientManager;
import org.jetbrains.io.jsonRpc.ClientManagerKt;
import org.jetbrains.io.jsonRpc.MessageServer;

@ChannelHandler.Sharable
final class MessageChannelHandler extends SimpleChannelInboundHandlerAdapter<WebSocketFrame> {
  private final ClientManager clientManager;
  private final MessageServer messageServer;

  MessageChannelHandler(@NotNull ClientManager clientManager, @NotNull MessageServer messageServer) {
    this.clientManager = clientManager;
    this.messageServer = messageServer;
  }

  @Override
  protected void messageReceived(ChannelHandlerContext context, WebSocketFrame message) throws Exception {
    WebSocketClient client = (WebSocketClient)context.attr(ClientManagerKt.getCLIENT()).get();
    if (message instanceof CloseWebSocketFrame) {
      if (client != null) {
        try {
          clientManager.disconnectClient(context, client, false);
        }
        finally {
          message.retain();
          client.disconnect((CloseWebSocketFrame)message);
        }
      }
    }
    else if (message instanceof PingWebSocketFrame) {
      context.channel().writeAndFlush(new PongWebSocketFrame(message.content()));
    }
    else if (message instanceof TextWebSocketFrame) {
      try {
        messageServer.messageReceived(client, ChannelBufferToString.readChars(message.content()));
      }
      catch (Throwable e) {
        clientManager.getExceptionHandler().exceptionCaught(e);
      }
    }
    else if (!(message instanceof PongWebSocketFrame)) {
      throw new UnsupportedOperationException(message.getClass().getName() + " frame types not supported");
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    Client client = context.attr(ClientManagerKt.getCLIENT()).get();
    // if null, so, has already been explicitly removed
    if (client != null) {
      clientManager.disconnectClient(context, client, false);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    try {
      clientManager.getExceptionHandler().exceptionCaught(cause);
    }
    finally {
      context.channel().close();
    }
  }
}
