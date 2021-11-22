package org.jetbrains.io.webSocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.jsonRpc.Client;

import java.nio.channels.ClosedChannelException;

public class WebSocketClient extends Client {
  private final WebSocketServerHandshaker handshaker;

  WebSocketClient(@NotNull Channel channel, @NotNull WebSocketServerHandshaker handshaker) {
    super(channel);

    this.handshaker = handshaker;
  }


  @Override
  public @NotNull ChannelFuture send(@NotNull ByteBuf message) {
    return sendFrame(message, false);
  }

  @NotNull
  public ChannelFuture sendFrame(@NotNull ByteBuf message, boolean binary) {
    if (channel.isOpen()) {
      return channel.writeAndFlush(binary ? new BinaryWebSocketFrame(message) : new TextWebSocketFrame(message));
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
