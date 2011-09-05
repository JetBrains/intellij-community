package org.jetbrains.jpsservice;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.jpsservice.impl.JpsServerMessageHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class Server {
  public static final int DEFAULT_SERVER_PORT = 7777;
  private static final int MAX_SIMULTANEOUS_BUILD_SESSIONS = Math.max(2, Runtime.getRuntime().availableProcessors());

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("jps-server");
  private final ChannelFactory myChannelFactory;
  private final ChannelPipelineFactory myPipelineFactory;
  private final ExecutorService myBuildsExecutor;

  public Server() {
    final ExecutorService threadPool = Executors.newCachedThreadPool();
    myBuildsExecutor = Executors.newFixedThreadPool(MAX_SIMULTANEOUS_BUILD_SESSIONS);
    myChannelFactory = new NioServerSocketChannelFactory(threadPool, threadPool, 1);
    myPipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new ChannelRegistrar(),
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(JpsRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          new JpsServerMessageHandler(myBuildsExecutor)
        );
      }
    };
  }

  public void start(int listenPort) {
    final ServerBootstrap bootstrap = new ServerBootstrap(myChannelFactory);
    bootstrap.setPipelineFactory(myPipelineFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    final Channel serverChannel = bootstrap.bind(new InetSocketAddress(listenPort));
    myAllOpenChannels.add(serverChannel);
  }

  public void stop() {
    try {
      myBuildsExecutor.shutdownNow();
      final ChannelGroupFuture closeFuture = myAllOpenChannels.close();
      closeFuture.awaitUninterruptibly();
    }
    finally {
      myChannelFactory.releaseExternalResources();
    }
  }

  public static void main(String[] args) {
    final Server server = new Server();
    // todo: define and use program arguments, i.e. listenPort etc.
    server.start(DEFAULT_SERVER_PORT);
    Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook thread") {
      public void run() {
        server.stop();
      }
    });
  }

  private class ChannelRegistrar extends SimpleChannelUpstreamHandler {
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      myAllOpenChannels.add(e.getChannel());
      super.channelOpen(ctx, e);
    }
  }
}
