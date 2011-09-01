package org.jetbrains.jpsservice;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.jpsservice.impl.JpsClientMessageHandler;
import org.jetbrains.jpsservice.impl.ProtoUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
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
  private final ConcurrentHashMap<UUID, JpsServerResponseHandler> myHandlers = new ConcurrentHashMap<UUID, JpsServerResponseHandler>();

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
              return myHandlers.get(sessionId);
            }

            protected void terminateSession(UUID sessionId) {
              final JpsServerResponseHandler handler = myHandlers.remove(sessionId);
              if (handler != null) {
                try {
                  handler.sessionTerminated();
                }
                catch (Throwable ignored) {
                  ignored.printStackTrace();
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

  // todo: return future?
  public boolean sendCompileRequest(String projectId, List<String> modules, boolean rebuild, JpsServerResponseHandler handler) throws Throwable {
    if (myState.get() != State.CONNECTED) {
      return false;
    }

    final UUID uuid = UUID.randomUUID();
    final JpsRemoteProto.Message.Request request = rebuild? ProtoUtil.createRebuildRequest(projectId, modules) : ProtoUtil.createMakeRequest(projectId, modules);
    myHandlers.put(uuid, handler);
    boolean success = false;
    try {
      final ChannelFuture future = Channels.write(myConnectFuture.getChannel(), ProtoUtil.toMessage(uuid, request));
      future.awaitUninterruptibly();
      success = future.isSuccess();
      return success;
    }
    finally {
      if (!success) {
        myHandlers.remove(uuid);
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
    return false;
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
}
