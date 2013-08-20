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
package org.jetbrains.jps.javac;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.*;
import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class JavacServer {
  public static final int DEFAULT_SERVER_PORT = 7878;
  public static final String SERVER_SUCCESS_START_MESSAGE = "Javac server started successfully. Listening on port: ";
  public static final String SERVER_ERROR_START_MESSAGE = "Error starting Javac Server: ";
  public static final String USE_ECLIPSE_COMPILER_PROPERTY = "use.eclipse.compiler";

  private ChannelRegistrar myChannelRegistrar;

  public void start(int listenPort) {
    final ServerBootstrap bootstrap = new ServerBootstrap().group(new NioEventLoopGroup(1, SharedThreadPool.getInstance())).channel(NioServerSocketChannel.class);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
    myChannelRegistrar = new ChannelRegistrar();
    final ChannelHandler compilationRequestsHandler = new CompilationRequestsHandler();
    bootstrap.childHandler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(myChannelRegistrar,
                                   new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(JavacRemoteProto.Message.getDefaultInstance()),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   compilationRequestsHandler);
      }
    });
    myChannelRegistrar.add(bootstrap.bind(listenPort).syncUninterruptibly().channel());
  }

  public void stop() {
    myChannelRegistrar.close().awaitUninterruptibly();
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
        @Override
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

  public static JavacRemoteProto.Message compile(final ChannelHandlerContext context,
                                                 final UUID sessionId,
                                                 List<String> options,
                                                 Collection<File> files,
                                                 Collection<File> classpath,
                                                 Collection<File> platformCp,
                                                 Collection<File> sourcePath,
                                                 Map<File, Set<File>> outs,
                                                 final CanceledStatus canceledStatus) {
    final DiagnosticOutputConsumer diagnostic = new DiagnosticOutputConsumer() {
      @Override
      public void javaFileLoaded(File file) {
        context.channel().writeAndFlush(JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createSourceFileLoadedResponse(file)));
      }

      @Override
      public void outputLineAvailable(String line) {
        context.channel().writeAndFlush(JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createStdOutputResponse(line)));
      }

      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        final JavacRemoteProto.Message.Response response = JavacProtoUtil.createBuildMessageResponse(diagnostic);
        context.channel().writeAndFlush(JavacProtoUtil.toMessage(sessionId, response));
      }

      @Override
      public void registerImports(String className, Collection<String> imports, Collection<String> staticImports) {
        final JavacRemoteProto.Message.Response response = JavacProtoUtil.createClassDataResponse(className, imports, staticImports);
        context.channel().writeAndFlush(JavacProtoUtil.toMessage(sessionId, response));
      }
    };

    final OutputFileConsumer outputSink = new OutputFileConsumer() {
      @Override
      public void save(@NotNull OutputFileObject fileObject) {
        context.channel().writeAndFlush(JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createOutputObjectResponse(fileObject)));
      }
    };

    try {
      final boolean rc = JavacMain.compile(options, files, classpath, platformCp, sourcePath, outs, diagnostic, outputSink, canceledStatus, System.getProperty(USE_ECLIPSE_COMPILER_PROPERTY) != null);
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createBuildCompletedResponse(rc));
    }
    catch (Throwable e) {
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure(e.getMessage(), e));
    }
  }

  private final Set<CancelHandler> myCancelHandlers = Collections.synchronizedSet(new HashSet<CancelHandler>());

  public void cancelBuilds() {
    synchronized (myCancelHandlers) {
      for (CancelHandler handler : myCancelHandlers) {
        handler.cancel();
      }
    }
  }

  private static List<File> toFiles(List<String> paths) {
    final List<File> files = new ArrayList<File>(paths.size());
    for (String path : paths) {
      files.add(new File(path));
    }
    return files;
  }

  @ChannelHandler.Sharable
  private class CompilationRequestsHandler extends SimpleChannelInboundHandler<JavacRemoteProto.Message> {
    @Override
    public void channelRead0(final ChannelHandlerContext context, JavacRemoteProto.Message message) throws Exception {
      final UUID sessionId = JavacProtoUtil.fromProtoUUID(message.getSessionId());
      final JavacRemoteProto.Message.Type messageType = message.getMessageType();

      JavacRemoteProto.Message reply = null;

      try {
        if (messageType == JavacRemoteProto.Message.Type.REQUEST) {
          final JavacRemoteProto.Message.Request request = message.getRequest();
          final JavacRemoteProto.Message.Request.Type requestType = request.getRequestType();
          if (requestType == JavacRemoteProto.Message.Request.Type.COMPILE) {
            final List<String> options = request.getOptionList();
            final List<File> files = toFiles(request.getFileList());
            final List<File> cp = toFiles(request.getClasspathList());
            final List<File> platformCp = toFiles(request.getPlatformClasspathList());
            final List<File> srcPath = toFiles(request.getSourcepathList());

            final Map<File, Set<File>> outs = new HashMap<File, Set<File>>();
            for (JavacRemoteProto.Message.Request.OutputGroup outputGroup : request.getOutputList()) {
              final Set<File> srcRoots = new HashSet<File>();
              for (String root : outputGroup.getSourceRootList()) {
                srcRoots.add(new File(root));
              }
              outs.put(new File(outputGroup.getOutputRoot()), srcRoots);
            }

            final CancelHandler cancelHandler = new CancelHandler();
            myCancelHandlers.add(cancelHandler);
            SharedThreadPool.getInstance().executeOnPooledThread(new Runnable() {
              @Override
              public void run() {
                try {
                  context.channel()
                    .writeAndFlush(compile(context, sessionId, options, files, cp, platformCp, srcPath, outs, cancelHandler));
                }
                finally {
                  myCancelHandlers.remove(cancelHandler);
                }
              }
            });
          }
          else if (requestType == JavacRemoteProto.Message.Request.Type.CANCEL){
            cancelBuilds();
            reply = JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createRequestAckResponse());
          }
          else if (requestType == JavacRemoteProto.Message.Request.Type.SHUTDOWN){
            cancelBuilds();
            new Thread("StopThread") {
              @Override
              public void run() {
                //noinspection finally
                try {
                  JavacServer.this.stop();
                }
                finally {
                  System.exit(0);
                }
              }
            }.start();
          }
          else {
            reply = JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure("Unsupported request type: " + requestType.name(), null));
          }
        }
        else {
          reply = JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure("Unsupported message: " + messageType.name(), null));
        }
      }
      finally {
        if (reply != null) {
          context.channel().writeAndFlush(reply);
        }
      }
    }
  }

  @ChannelHandler.Sharable
  private static final class ChannelRegistrar extends ChannelInboundHandlerAdapter {
    private final ChannelGroup openChannels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

    public boolean isEmpty() {
      return openChannels.isEmpty();
    }

    public void add(@NotNull Channel serverChannel) {
      assert serverChannel instanceof ServerChannel;
      openChannels.add(serverChannel);
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
      // we don't need to remove channel on close - ChannelGroup do it
      openChannels.add(context.channel());

      super.channelActive(context);
    }

    public ChannelGroupFuture close() {
      EventLoopGroup eventLoopGroup = null;
      for (Channel channel : openChannels) {
        if (channel instanceof ServerChannel) {
          eventLoopGroup = channel.eventLoop().parent();
          break;
        }
      }

      ChannelGroupFuture future;
      try {
        future = openChannels.close();
      }
      finally {
        assert eventLoopGroup != null;
        eventLoopGroup.shutdownGracefully();
      }
      return future;
    }
  }

  private static class CancelHandler implements CanceledStatus {
    private volatile boolean myIsCanceled = false;

    private CancelHandler() {
    }

    public void cancel() {
      myIsCanceled = true;
    }

    @Override
    public boolean isCanceled() {
      return myIsCanceled;
    }
  }
}