// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.jsonRpc;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.IntObjectMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

public abstract class Client extends UserDataHolderBase {
  protected final Channel channel;

  final IntObjectMap<AsyncPromise<Object>> messageCallbackMap =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  protected Client(@NotNull Channel channel) {
    this.channel = channel;
  }

  public final @NotNull EventLoop getEventLoop() {
    return channel.eventLoop();
  }

  public final @NotNull ByteBufAllocator getByteBufAllocator() {
    return channel.alloc();
  }

  protected abstract @NotNull ChannelFuture send(@NotNull ByteBuf message);

  public abstract void sendHeartbeat();

  final @Nullable <T> Promise<T> send(int messageId, @NotNull ByteBuf message) {
    ChannelFuture channelFuture = send(message);
    if (messageId == -1) {
      return null;
    }

    AsyncPromise<T> promise = new AsyncPromise<T>().onError(error -> messageCallbackMap.remove(messageId));

    channelFuture.addListener(future -> {
      if (!future.isSuccess()) {
        Throwable cause = future.cause();
        if (cause == null) {
          promise.setError("No success"); //NON-NLS
        }
        else {
          promise.setError(cause);
        }
      }
    });
    messageCallbackMap.put(messageId, (AsyncPromise<Object>)promise);
    return promise;
  }

  final void rejectAsyncResults(@NotNull ExceptionHandler exceptionHandler) {
    if (!messageCallbackMap.isEmpty()) {
      for (AsyncPromise<?> promise : messageCallbackMap.values()) {
        try {
          promise.setError("rejected");
        }
        catch (Throwable e) {
          exceptionHandler.exceptionCaught(e);
        }
      }
    }
  }
}