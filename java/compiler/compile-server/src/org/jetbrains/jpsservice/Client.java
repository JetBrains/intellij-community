package org.jetbrains.jpsservice;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.impl.JpsClientMessageHandler;
import org.jetbrains.jpsservice.impl.ProtoUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class Client {
  private static enum State {
    DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
  }
  private final AtomicReference<State> myState = new AtomicReference<State>(State.DISCONNECTED);

  private final ChannelPipelineFactory myPipelineFactory;
  private final ChannelFactory myChannelFactory;
  private ChannelFuture myConnectFuture;
  private final ConcurrentHashMap<UUID, RequestFuture> myHandlers = new ConcurrentHashMap<UUID, RequestFuture>();

  public Client() {
    myChannelFactory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 1);

    myPipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(JpsRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          new JpsClientMessageHandler() {

            protected JpsServerResponseHandler getHandler(UUID sessionId) {
              final RequestFuture future = myHandlers.get(sessionId);
              return future != null? future.myHandler : null;
            }

            protected void terminateSession(UUID sessionId) {
              final RequestFuture future = myHandlers.remove(sessionId);
              if (future != null) {
                final JpsServerResponseHandler handler = future.myHandler;
                try {
                  if (handler != null) {
                    try {
                      handler.sessionTerminated();
                    }
                    catch (Throwable ignored) {
                      ignored.printStackTrace();
                    }
                  }
                }
                finally {
                  future.setDone();
                }
              }
            }

            public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
              try {
                super.channelClosed(ctx, e);
              }
              finally {
                for (UUID uuid : new ArrayList<UUID>(myHandlers.keySet())) {
                  terminateSession(uuid);
                }
              }
            }
          }
        );
      }
    };
  }

  public Future sendCompileRequest(String projectId, List<String> modules, boolean rebuild, JpsServerResponseHandler handler) {
    if (myState.get() != State.CONNECTED) {
      return null;
    }
    return sendRequest(
      rebuild? ProtoUtil.createRebuildRequest(projectId, modules) : ProtoUtil.createMakeRequest(projectId, modules),
      handler
    );
  }

  @Nullable
  public Future sendShutdownRequest() throws Throwable {
    if (myState.get() != State.CONNECTED) {
      return null;
    }
    return sendRequest(ProtoUtil.createShutdownRequest(true), null);
  }

  private Future sendRequest(JpsRemoteProto.Message.Request request, @Nullable JpsServerResponseHandler handler) {
    final UUID sessionUUID = UUID.randomUUID();
    final RequestFuture requestFuture = new RequestFuture(handler);
    myHandlers.put(sessionUUID, requestFuture);
    boolean success = false;
    try {
      final ChannelFuture future = Channels.write(myConnectFuture.getChannel(), ProtoUtil.toMessage(sessionUUID, request));
      future.awaitUninterruptibly();
      success = future.isSuccess();
      return success? requestFuture : null;
    }
    finally {
      if (!success) {
        requestFuture.setDone();
        myHandlers.remove(sessionUUID);
        handler.sessionTerminated();
      }
    }
  }

  public boolean connect(final String host, final int port) throws Throwable {
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

  public void disconnect() {
    if (myState.compareAndSet(State.CONNECTED, State.DISCONNECTING)) {
      try {
        final ChannelFuture future = myConnectFuture;
        if (future != null) {
          try {
            final ChannelFuture closeFuture = future.getChannel().close();
            closeFuture.awaitUninterruptibly();
          }
          finally {
            myChannelFactory.releaseExternalResources();
          }
        }
      }
      finally {
        myConnectFuture = null;
        myState.compareAndSet(State.DISCONNECTING, State.DISCONNECTED);
      }
    }
  }

  public boolean isConnected() {
    return myState.get() == State.CONNECTED;
  }

  private static class RequestFuture implements Future {
    private final Semaphore mySemaphore = new Semaphore(1);
    private final AtomicBoolean myDone = new AtomicBoolean(false);
    private final JpsServerResponseHandler myHandler;

    public RequestFuture(JpsServerResponseHandler handler) {
      myHandler = handler;
      mySemaphore.acquireUninterruptibly();
    }

    public void setDone() {
      if (!myDone.getAndSet(true)) {
        mySemaphore.release();
      }
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    public boolean isCancelled() {
      return false;
    }

    public boolean isDone() {
      return myDone.get();
    }

    public Object get() throws InterruptedException, ExecutionException {
      while (!isDone()) {
        mySemaphore.tryAcquire(100L, TimeUnit.MILLISECONDS);
      }
      return null;
    }

    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      if (!isDone()) {
        mySemaphore.tryAcquire(timeout, unit);
      }
      return null;
    }
  }
}
