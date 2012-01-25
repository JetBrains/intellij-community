package org.jetbrains.jps.server;

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
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.JpsRemoteProto;
import org.jetbrains.jps.incremental.Paths;

import java.io.File;
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
  public static final String SERVER_SUCCESS_START_MESSAGE = "Compile Server started successfully. Listening on port: ";
  public static final String SERVER_ERROR_START_MESSAGE = "Error starting Compile Server: ";

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("compile-server");
  private final ChannelFactory myChannelFactory;
  private final ChannelPipelineFactory myPipelineFactory;
  private final ExecutorService myBuildsExecutor;

  public Server(File systemDir) {
    Paths.getInstance().setSystemRoot(systemDir);
    final ExecutorService threadPool = Executors.newCachedThreadPool();
    myBuildsExecutor = Executors.newFixedThreadPool(MAX_SIMULTANEOUS_BUILD_SESSIONS);
    myChannelFactory = new NioServerSocketChannelFactory(threadPool, threadPool, 1);
    final ChannelRegistrar channelRegistrar = new ChannelRegistrar();
    final ServerMessageHandler messageHandler = new ServerMessageHandler(myBuildsExecutor, this);
    myPipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          channelRegistrar,
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(JpsRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          messageHandler
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
    try {
      int port = DEFAULT_SERVER_PORT;
      File systemDir = null;
      if (args.length > 0) {
        try {
          port = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e) {
          System.err.println("Error parsing port: " + e.getMessage());
          System.exit(-1);
        }

        systemDir = new File(args[1]);
      }

      final Server server = new Server(systemDir);
      server.start(port);
      Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook thread") {
        public void run() {
          server.stop();
        }
      });

      ServerState.getInstance().setKeepTempCachesInMemory(System.getProperty(GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION) != null);

      System.out.println("Server classpath: " + System.getProperty("java.class.path"));
      System.err.println(SERVER_SUCCESS_START_MESSAGE + port);
    }
    catch (Throwable e) {
      System.err.println(SERVER_ERROR_START_MESSAGE + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(-1);
    }
  }

  private class ChannelRegistrar extends SimpleChannelUpstreamHandler {
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      myAllOpenChannels.add(e.getChannel());
      super.channelOpen(ctx, e);
    }
  }
}
