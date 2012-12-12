/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.client;

import com.google.protobuf.MessageLite;
import com.intellij.openapi.diagnostic.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.RequestFuture;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class SimpleProtobufClient<T extends ProtobufResponseHandler> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.client.SimpleProtobufClient");

  private static enum State {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
  }

  private final AtomicReference<State> myState = new AtomicReference<State>(State.DISCONNECTED);
  protected final ChannelPipelineFactory myPipelineFactory;
  protected final ChannelFactory myChannelFactory;
  protected volatile ChannelFuture myConnectFuture;
  private final ProtobufClientMessageHandler<T> myMessageHandler;

  public SimpleProtobufClient(final MessageLite msgDefaultInstance, final Executor asyncExec, final UUIDGetter uuidGetter) {
    myMessageHandler = new ProtobufClientMessageHandler<T>(uuidGetter, this, asyncExec);
    myChannelFactory = new NioClientSocketChannelFactory(asyncExec, asyncExec, 1);
    myPipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(msgDefaultInstance),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          myMessageHandler
        );
      }
    };
  }

  public final void checkConnected() throws Exception {
    if (myState.get() != State.CONNECTED) {
      throw new Exception("Client not connected");
    }
  }

  public final boolean connect(final String host, final int port) throws Throwable {
    if (myState.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
      boolean success = false;

      try {
        final ClientBootstrap bootstrap = new ClientBootstrap(myChannelFactory);
        bootstrap.setPipelineFactory(myPipelineFactory);
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
        final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));
        future.awaitUninterruptibly();

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
        else {
          final Throwable reason = future.getCause();
          if (reason != null) {
            throw reason;
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

  protected void onConnect() {
  }
  protected void beforeDisconnect() {
  }
  protected void onDisconnect() {
  }

  public final void disconnect() {
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
          final ChannelFuture closeFuture = future.getChannel().close();
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

  public final boolean isConnected() {
    return myState.get() == State.CONNECTED;
  }

  public final RequestFuture<T> sendMessage(final UUID messageId, MessageLite message, @Nullable final T responseHandler, @Nullable final RequestFuture.CancelAction<T> cancelAction) {
    final RequestFuture<T> requestFuture = new RequestFuture<T>(responseHandler, messageId, cancelAction);
    myMessageHandler.registerFuture(messageId, requestFuture);
    final ChannelFuture connectFuture = myConnectFuture;
    final Channel channel = connectFuture != null? connectFuture.getChannel() : null;
    if (channel != null && channel.isConnected()) {
      Channels.write(channel, message).addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
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
