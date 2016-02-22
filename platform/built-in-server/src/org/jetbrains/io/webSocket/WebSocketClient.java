package org.jetbrains.io.webSocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.jsonRpc.Client;

import java.nio.channels.ClosedChannelException;

class WebSocketClient extends Client {
  private final WebSocketServerHandshaker handshaker;

  public WebSocketClient(@NotNull Channel channel, @NotNull WebSocketServerHandshaker handshaker) {
    super(channel);

    this.handshaker = handshaker;
  }

  @NotNull
  @Override
  public ChannelFuture send(@NotNull ByteBuf message) {
    if (channel.isOpen()) {
      return channel.writeAndFlush(new TextWebSocketFrame(message));
    }
    else {
      return channel.newFailedFuture(new ClosedChannelException());
    }
  }

  @Override
  public void sendHeartbeat() {
    channel.writeAndFlush(new PingWebSocketFrame());
  }

  public void disconnect(@NotNull CloseWebSocketFrame frame) {
    handshaker.close(channel, frame);
  }
}