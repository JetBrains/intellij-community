/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4JLoggerFactory;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12
 */
public class ExternalJavacProcess {
  public static final String JPS_JAVA_COMPILING_TOOL_PROPERTY = "jps.java.compiling.tool";
  private final ChannelInitializer myChannelInitializer;
  private final EventLoopGroup myEventLoopGroup;
  private volatile ChannelFuture myConnectFuture;
  private volatile CancelHandler myCancelHandler;

  static {
    org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
    if (!root.getAllAppenders().hasMoreElements()) {
      root.setLevel(Level.INFO);
      root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN)));
    }
    InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory());
  }
  
  public ExternalJavacProcess() {
    final JavacRemoteProto.Message msgDefaultInstance = JavacRemoteProto.Message.getDefaultInstance();
    
    myEventLoopGroup = new NioEventLoopGroup(1, SharedThreadPool.getInstance());
    myChannelInitializer = new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(msgDefaultInstance),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   new CompilationRequestsHandler()
                                   );
      }
    };
  }
  
  //static volatile long myGlobalStart;
  
  public static void main(String[] args) {
    //myGlobalStart = System.currentTimeMillis();
    UUID uuid = null;
    String host = null;
    int port = -1;
    if (args.length > 0) {
      try {
        uuid = UUID.fromString(args[0]);
      }
      catch (Exception e) {
        System.err.println("Error parsing session id: " + e.getMessage());
        System.exit(-1);
      }
      
      host = args[1];
      
      try {
        port = Integer.parseInt(args[2]);
      }
      catch (NumberFormatException e) {
        System.err.println("Error parsing port: " + e.getMessage());
        System.exit(-1);
      }
    }
    else {
      System.err.println("Insufficient parameters");
      System.exit(-1);
    }

    final ExternalJavacProcess process = new ExternalJavacProcess();
    try {
      //final long connectStart = System.currentTimeMillis();
      if (process.connect(host, port)) {
        //final long connectEnd = System.currentTimeMillis();
        //System.err.println("Connected in " + (connectEnd - connectStart) + " ms; since start: " + (connectEnd - myGlobalStart));
        process.myConnectFuture.channel().writeAndFlush(
          JavacProtoUtil.toMessage(uuid, JavacProtoUtil.createRequestAckResponse())
        );
      }
      else {
        System.err.println("Failed to connect to parent process");
        System.exit(-1);
      }
    }
    catch (Throwable throwable) {
      throwable.printStackTrace(System.err);
      System.exit(-1);
    }
  }

  private  boolean connect(final String host, final int port) throws Throwable {
    final Bootstrap bootstrap = new Bootstrap().group(myEventLoopGroup).channel(NioSocketChannel.class).handler(myChannelInitializer);
    bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
    final ChannelFuture future = bootstrap.connect(host, port).syncUninterruptibly();
    if (future.isSuccess()) {
      myConnectFuture = future;
      return true;
    }
    return false;
  }

  private static JavacRemoteProto.Message compile(final ChannelHandlerContext context,
                                                 final UUID sessionId,
                                                 List<String> options,
                                                 Collection<File> files,
                                                 Collection<File> classpath,
                                                 Collection<File> platformCp,
                                                 Collection<File> modulePath,
                                                 Collection<File> sourcePath,
                                                 Map<File, Set<File>> outs,
                                                 final CanceledStatus canceledStatus) {
    //final long compileStart = System.currentTimeMillis();
    //System.err.println("Compile start; since global start: " + (compileStart - myGlobalStart));
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
      JavaCompilingTool tool = getCompilingTool();
      final boolean rc = JavacMain.compile(
        options, files, classpath, platformCp, modulePath, sourcePath, outs, diagnostic, outputSink, canceledStatus, tool
      );
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createBuildCompletedResponse(rc));
    }
    catch (Throwable e) {
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure(e.getMessage(), e));
    }
    //finally {
    //  final long compileEnd = System.currentTimeMillis();
    //  System.err.println("Compiled in " + (compileEnd - compileStart) + " ms; since global start: " + (compileEnd - myGlobalStart));
    //}
  }
  
  private static JavaCompilingTool getCompilingTool() {
    String property = System.getProperty(JPS_JAVA_COMPILING_TOOL_PROPERTY);
    if (property != null) {
      JavaCompilingTool tool = JavaBuilderUtil.findCompilingTool(property);
      if (tool != null) {
        return tool;
      }
    }
    return new JavacCompilerTool();
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
            if (myCancelHandler == null) { // if not running yet
              final List<String> options = request.getOptionList();
              final List<File> files = toFiles(request.getFileList());
              final List<File> cp = toFiles(request.getClasspathList());
              final List<File> platformCp = toFiles(request.getPlatformClasspathList());
              final List<File> srcPath = toFiles(request.getSourcepathList());
              final List<File> modulePath = toFiles(request.getModulePathList());

              final Map<File, Set<File>> outs = new HashMap<File, Set<File>>();
              for (JavacRemoteProto.Message.Request.OutputGroup outputGroup : request.getOutputList()) {
                final Set<File> srcRoots = new HashSet<File>();
                for (String root : outputGroup.getSourceRootList()) {
                  srcRoots.add(new File(root));
                }
                outs.put(new File(outputGroup.getOutputRoot()), srcRoots);
              }

              final CancelHandler cancelHandler = new CancelHandler();
              myCancelHandler = cancelHandler;
              SharedThreadPool.getInstance().executeOnPooledThread(new Runnable() {
                @Override
                public void run() {
                  try {
                    context.channel().writeAndFlush(
                      compile(context, sessionId, options, files, cp, platformCp, modulePath, srcPath, outs, cancelHandler)
                    ).awaitUninterruptibly();
                  }
                  finally {
                    myCancelHandler = null;
                    ExternalJavacProcess.this.stop();
                  }
                }
              });
            }
          }
          else if (requestType == JavacRemoteProto.Message.Request.Type.CANCEL){
            cancelBuild();
          }
          else if (requestType == JavacRemoteProto.Message.Request.Type.SHUTDOWN){
            cancelBuild();
            new Thread("StopThread") {
              @Override
              public void run() {
                //noinspection finally
                ExternalJavacProcess.this.stop();
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
  
  public void stop() {
    try {
      //final long stopStart = System.currentTimeMillis();
      //System.err.println("Exiting. Since global start " + (stopStart - myGlobalStart));
      final ChannelFuture future = myConnectFuture;
      if (future != null) {
        future.channel().close().await();
      }
      myEventLoopGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS).await();
      //final long stopEnd = System.currentTimeMillis();
      //System.err.println("Stop completed in " + (stopEnd - stopStart) + "ms; since global start: " + ((stopEnd - myGlobalStart)));
      System.exit(0);
    }
    catch (Throwable e) {
      e.printStackTrace(System.err);
      System.exit(-1);
    }
  }

  private static List<File> toFiles(List<String> paths) {
    final List<File> files = new ArrayList<File>(paths.size());
    for (String path : paths) {
      files.add(new File(path));
    }
    return files;
  }
  
  public void cancelBuild() {
    final CancelHandler cancelHandler = myCancelHandler;
    if (cancelHandler != null) {
      cancelHandler.cancel();
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
