// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.client;

import com.google.protobuf.MessageLite;
import com.intellij.openapi.diagnostic.Logger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.RequestFuture;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 */
public final class SimpleProtobufClient<T extends ProtobufResponseHandler> {
  private static final Logger LOG = Logger.getInstance(SimpleProtobufClient.class);

  private enum State {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
  }

  private final AtomicReference<State> myState = new AtomicReference<>(State.DISCONNECTED);
  private final ChannelInitializer myChannelInitializer;
  private final EventLoopGroup myEventLoopGroup;
  private volatile ChannelFuture myConnectFuture;
  private final ProtobufClientMessageHandler<T> myMessageHandler;

  public SimpleProtobufClient(final MessageLite msgDefaultInstance, final Executor asyncExec, final UUIDGetter uuidGetter) {
    myMessageHandler = new ProtobufClientMessageHandler<>(uuidGetter, this, asyncExec);
    myEventLoopGroup = new NioEventLoopGroup(1, asyncExec);
    myChannelInitializer = new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) {
        channel.pipeline().addLast(new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(msgDefaultInstance),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   myMessageHandler);
      }
    };
  }

  public void checkConnected() throws Exception {
    if (myState.get() != State.CONNECTED) {
      throw new Exception("Client not connected");
    }
  }

  public boolean connect(final String host, final int port) {
    if (myState.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
      boolean success = false;

      try {
        final Bootstrap bootstrap = new Bootstrap().group(myEventLoopGroup).channel(NioSocketChannel.class).handler(myChannelInitializer);
        bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
        final ChannelFuture future = bootstrap.connect(host, port).syncUninterruptibly();
        success = future.isSuccess();
        if (success) {
          myConnectFuture = future;
          try {
            onConnect();
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
        return success;
      }
      finally {
        myState.compareAndSet(State.CONNECTING, success? State.CONNECTED : State.DISCONNECTED);
      }
    }
    // already connected
    return true;
  }

  private void onConnect() {
  }
  private void beforeDisconnect() {
  }
  private void onDisconnect() {
  }

  public void disconnect() {
    if (myState.compareAndSet(State.CONNECTED, State.DISCONNECTING)) {
      try {
        final ChannelFuture future = myConnectFuture;
        if (future != null) {
          try {
            beforeDisconnect();
          }
          catch (Throwable e) {
            LOG.error(e);
          }
          final ChannelFuture closeFuture = future.channel().close();
          closeFuture.awaitUninterruptibly();
        }
      }
      finally {
        myConnectFuture = null;
        myState.compareAndSet(State.DISCONNECTING, State.DISCONNECTED);
        try {
          onDisconnect();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  public boolean isConnected() {
    return myState.get() == State.CONNECTED;
  }

  public RequestFuture<T> sendMessage(final UUID messageId,
                                      MessageLite message,
                                      final @Nullable T responseHandler,
                                      final @Nullable RequestFuture.CancelAction<T> cancelAction) {
    final RequestFuture<T> requestFuture = new RequestFuture<>(responseHandler, messageId, cancelAction);
    myMessageHandler.registerFuture(messageId, requestFuture);
    final ChannelFuture connectFuture = myConnectFuture;
    final Channel channel = connectFuture != null? connectFuture.channel() : null;
    if (channel != null && channel.isActive()) {
      channel.writeAndFlush(message).addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
          if (!future.isSuccess()) {
            notifyTerminated(messageId, requestFuture, responseHandler);
          }
        }
      });
    }
    else {
      notifyTerminated(messageId, requestFuture, responseHandler);
    }
    return requestFuture;
  }

  private void notifyTerminated(UUID messageId, RequestFuture<T> requestFuture, @Nullable T responseHandler) {
    try {
      myMessageHandler.removeFuture(messageId);
      if (responseHandler != null) {
        responseHandler.sessionTerminated();
      }
    }
    finally {
      requestFuture.setDone();
    }
  }
}
