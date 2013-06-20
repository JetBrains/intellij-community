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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class JavacServer {
  public static final int DEFAULT_SERVER_PORT = 7878;
  public static final String SERVER_SUCCESS_START_MESSAGE = "Javac server started successfully. Listening on port: ";
  public static final String SERVER_ERROR_START_MESSAGE = "Error starting Javac Server: ";
  public static final String USE_ECLIPSE_COMPILER_PROPERTY = "use.eclipse.compiler";

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("javac-server");
  private final ChannelFactory myChannelFactory;
  private final ChannelPipelineFactory myPipelineFactory;

  public JavacServer() {
    myChannelFactory = new NioServerSocketChannelFactory(SharedThreadPool.getInstance(), SharedThreadPool.getInstance(), 1);
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


  public static JavacRemoteProto.Message compile(final ChannelHandlerContext ctx,
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
        Channels.write(ctx.getChannel(), JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createSourceFileLoadedResponse(file)));
      }

      public void outputLineAvailable(String line) {
        Channels.write(ctx.getChannel(), JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createStdOutputResponse(line)));
      }

      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        final JavacRemoteProto.Message.Response response = JavacProtoUtil.createBuildMessageResponse(diagnostic);
        Channels.write(ctx.getChannel(), JavacProtoUtil.toMessage(sessionId, response));
      }

      @Override
      public void registerImports(String className, Collection<String> imports, Collection<String> staticImports) {
        final JavacRemoteProto.Message.Response response = JavacProtoUtil.createClassDataResponse(className, imports, staticImports);
        Channels.write(ctx.getChannel(), JavacProtoUtil.toMessage(sessionId, response));
      }
    };

    final OutputFileConsumer outputSink = new OutputFileConsumer() {
      public void save(@NotNull OutputFileObject fileObject) {
        Channels.write(ctx.getChannel(), JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createOutputObjectResponse(fileObject)));
      }
    };

    try {
      final boolean rc = JavacMain.compile(options, files, classpath, platformCp, sourcePath, outs, diagnostic, outputSink, canceledStatus, System.getProperty(USE_ECLIPSE_COMPILER_PROPERTY) != null);
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createBuildCompletedResponse(rc));
    }
    catch (Throwable e) {
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

  private class CompilationRequestsHandler extends SimpleChannelHandler {

    public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      final JavacRemoteProto.Message msg = (JavacRemoteProto.Message)e.getMessage();
      final UUID sessionId = JavacProtoUtil.fromProtoUUID(msg.getSessionId());
      final JavacRemoteProto.Message.Type messageType = msg.getMessageType();

      JavacRemoteProto.Message reply = null;

      try {
        if (messageType == JavacRemoteProto.Message.Type.REQUEST) {
          final JavacRemoteProto.Message.Request request = msg.getRequest();
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
              public void run() {
                try {
                  final JavacRemoteProto.Message exitMsg =
                    compile(ctx, sessionId, options, files, cp, platformCp, srcPath, outs, cancelHandler);
                  Channels.write(ctx.getChannel(), exitMsg);
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
              public void run() {
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
          Channels.write(ctx.getChannel(), reply);
        }
      }
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

  private static class CancelHandler implements CanceledStatus {
    private volatile boolean myIsCanceled = false;

    private CancelHandler() {
    }

    public void cancel() {
      myIsCanceled = true;
    }

    public boolean isCanceled() {
      return myIsCanceled;
    }
  }
}