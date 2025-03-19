// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.jsonRpc.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.jsonRpc.Client;

import java.nio.channels.ClosedChannelException;

public class SocketClient extends Client {
  protected SocketClient(@NotNull Channel channel) {
    super(channel);
  }

  @Override
  public @NotNull ChannelFuture send(@NotNull ByteBuf message) {
    if (channel.isOpen()) {
      ByteBuf lengthBuffer = channel.alloc().ioBuffer(4);
      lengthBuffer.writeInt(message.readableBytes());
      channel.write(lengthBuffer);
      return channel.writeAndFlush(message);
    }
    else {
      return channel.newFailedFuture(new ClosedChannelException());
    }
  }

  @Override
  public void sendHeartbeat() {
  }
}