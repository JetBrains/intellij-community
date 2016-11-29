package org.jetbrains.io.jsonRpc;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

import java.util.Enumeration;

public abstract class Client extends UserDataHolderBase {
  protected final Channel channel;

  final ConcurrentIntObjectMap<AsyncPromise<Object>> messageCallbackMap = ContainerUtil.createConcurrentIntObjectMap();

  protected Client(@NotNull Channel channel) {
    this.channel = channel;
  }

  @NotNull
  public final EventLoop getEventLoop() {
    return channel.eventLoop();
  }

  @NotNull
  public final ByteBufAllocator getByteBufAllocator() {
    return channel.alloc();
  }

  @NotNull
  protected abstract ChannelFuture send(@NotNull ByteBuf message);

  public abstract void sendHeartbeat();

  @Nullable
  final <T> AsyncPromise<T> send(int messageId, @NotNull ByteBuf message) {
    ChannelFuture channelFuture = send(message);
    if (messageId == -1) {
      return null;
    }

    ChannelFutureAwarePromise<T> promise = new ChannelFutureAwarePromise<>(messageId, messageCallbackMap);
    channelFuture.addListener(promise);
    //noinspection unchecked
    messageCallbackMap.put(messageId, (AsyncPromise<Object>)promise);
    return promise;
  }

  final void rejectAsyncResults(@NotNull ExceptionHandler exceptionHandler) {
    if (!messageCallbackMap.isEmpty()) {
      Enumeration<AsyncPromise<Object>> elements = messageCallbackMap.elements();
      while (elements.hasMoreElements()) {
        try {
          elements.nextElement().setError("rejected");
        }
        catch (Throwable e) {
          exceptionHandler.exceptionCaught(e);
        }
      }
    }
  }

  private static final class ChannelFutureAwarePromise<T> extends AsyncPromise<T> implements ChannelFutureListener {
    private final int messageId;
    private final ConcurrentIntObjectMap<?> messageCallbackMap;

    public ChannelFutureAwarePromise(int messageId, ConcurrentIntObjectMap<?> messageCallbackMap) {
      this.messageId = messageId;
      this.messageCallbackMap = messageCallbackMap;
    }

    @Override
    public boolean setError(@NotNull Throwable error) {
      boolean result = super.setError(error);
      messageCallbackMap.remove(messageId);
      return result;
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
      if (!future.isSuccess()) {
        Throwable cause = future.cause();
        if (cause == null) {
          setError("No success");
        }
        else {
          setError(cause);
        }
      }
    }
  }
}