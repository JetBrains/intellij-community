package org.jetbrains.io.webSocket;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Enumeration;

public abstract class Client extends UserDataHolderBase {
  protected final Channel channel;

  final ConcurrentIntObjectMap<AsyncResult> messageCallbackMap = ContainerUtil.createConcurrentIntObjectMap();

  protected Client(@NotNull Channel channel) {
    this.channel = channel;
  }

  @NotNull
  public EventLoop getEventLoop() {
    return channel.eventLoop();
  }

  protected abstract ChannelFuture send(@NotNull ByteBuf message);

  abstract void sendHeartbeat();

  @Nullable
  final <T> AsyncResult<T> send(int messageId, ByteBuf message) {
    ChannelFuture channelFuture = send(message);
    if (messageId == -1) {
      return null;
    }

    ChannelFutureAwareAsyncResult<T> callback = new ChannelFutureAwareAsyncResult<T>(messageId, messageCallbackMap);
    channelFuture.addListener(callback);
    messageCallbackMap.put(messageId, callback);
    return callback;
  }

  void rejectAsyncResults(ExceptionHandler exceptionHandler) {
    if (!messageCallbackMap.isEmpty()) {
      Enumeration<AsyncResult> elements = messageCallbackMap.elements();
      while (elements.hasMoreElements()) {
        try {
          elements.nextElement().setRejected();
        }
        catch (Throwable e) {
          exceptionHandler.exceptionCaught(e);
        }
      }
    }
  }

  private static final class ChannelFutureAwareAsyncResult<T> extends AsyncResult<T> implements ChannelFutureListener {
    private final int messageId;
    private final ConcurrentIntObjectMap<AsyncResult> messageCallbackMap;

    public ChannelFutureAwareAsyncResult(int messageId, ConcurrentIntObjectMap<AsyncResult> messageCallbackMap) {
      this.messageId = messageId;
      this.messageCallbackMap = messageCallbackMap;
    }

    @Override
    public void setRejected() {
      super.setRejected();

      if (isProcessed()) {
        messageCallbackMap.remove(messageId);
      }
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
      if (!isProcessed() && !future.isSuccess()) {
        setRejected();
      }
    }
  }
}