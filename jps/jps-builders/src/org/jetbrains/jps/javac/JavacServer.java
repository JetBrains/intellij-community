package org.jetbrains.jps.javac;

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

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class JavacServer {
  public static final int DEFAULT_SERVER_PORT = 7878;
  public static final String SERVER_SUCCESS_START_MESSAGE = "Javac server started successfully. Listening on port: ";
  public static final String SERVER_ERROR_START_MESSAGE = "Error starting Javac Server: ";

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("javac-server");
  private final ChannelFactory myChannelFactory;
  private final ChannelPipelineFactory myPipelineFactory;

  public JavacServer() {
    final ExecutorService threadPool = Executors.newCachedThreadPool();
    myChannelFactory = new NioServerSocketChannelFactory(threadPool, threadPool, 1);
    final ChannelRegistrar channelRegistrar = new ChannelRegistrar();
    final ChannelHandler compilationRequestsHandler = new CompilationRequestsHandler();
    myPipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          channelRegistrar,
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(JavacRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          compilationRequestsHandler
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
      if (args.length > 0) {
        try {
          port = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException e) {
          System.err.println("Error parsing port: " + e.getMessage());
          System.exit(-1);
        }
      }

      final JavacServer server = new JavacServer();
      server.start(port);
      Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook thread") {
        public void run() {
          server.stop();
        }
      });

      System.out.println("Server classpath: " + System.getProperty("java.class.path"));
      System.err.println(SERVER_SUCCESS_START_MESSAGE + port);
    }
    catch (Throwable e) {
      System.err.println(SERVER_ERROR_START_MESSAGE + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(-1);
    }
  }

  private static class CompilationRequestsHandler extends SimpleChannelHandler {

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      // todo
    }

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
      super.exceptionCaught(ctx, e);
    }
  }

  private class ChannelRegistrar extends SimpleChannelUpstreamHandler {
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      myAllOpenChannels.add(e.getChannel());
      super.channelOpen(ctx, e);
    }
  }
}
