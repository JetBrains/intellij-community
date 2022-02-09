// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import io.netty.util.internal.logging.JdkLoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.javac.ast.api.JavacFileData;

import javax.tools.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.logging.Formatter;

/**
 * @author Eugene Zhuravlev
 */
public final class ExternalJavacProcess {
  public static final String JPS_JAVA_COMPILING_TOOL_PROPERTY = "jps.java.compiling.tool";
  private final ChannelInitializer<?> myChannelInitializer;
  private final EventLoopGroup myEventLoopGroup;
  private final boolean myKeepRunning;
  private volatile ChannelFuture myConnectFuture;
  private final ConcurrentMap<UUID, Boolean> myCanceled = new ConcurrentHashMap<UUID, Boolean>();
  private final Executor myThreadPool = Executors.newCachedThreadPool();

  static {
    Logger root = Logger.getLogger("");
    if (root.getHandlers().length == 0) {
      root.setLevel(Level.INFO);
      ConsoleHandler handler = new ConsoleHandler();
      handler.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          return record.getMessage() + "\n";
        }
      });
      root.addHandler(handler);
    }
    InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
  }

  public ExternalJavacProcess(boolean keepRunning) {
    myKeepRunning = keepRunning;
    final JavacRemoteProto.Message msgDefaultInstance = JavacRemoteProto.Message.getDefaultInstance();

    myEventLoopGroup = new NioEventLoopGroup(1, myThreadPool);
    myChannelInitializer = new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) {
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

  /**
   * @param args: SessionUUID, host, port,
   */
  public static void main(String[] args) {
    //myGlobalStart = System.nanoTime();
    UUID uuid = null;
    String host = null;
    int port = -1;
    boolean keepRunning = false;  // keep running after compilation ends until explicit shutdown
    if (args.length >= 3) {
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

      if (args.length > 3) {
        keepRunning = Boolean.valueOf(args[3]);
      }
    }
    else {
      System.err.println("Insufficient number of parameters");
      System.exit(-1);
    }

    final ExternalJavacProcess process = new ExternalJavacProcess(keepRunning);
    try {
      //final long connectStart = System.nanoTime();
      if (process.connect(host, port)) {
        //final long connectEnd = System.nanoTime();
        //System.err.println("Connected in " + TimeUnit.NANOSECONDS.toMillis(connectEnd - connectStart) + " ms; since start: " + TimeUnit.NANOSECONDS.toMillis(connectEnd - myGlobalStart));
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
                                                  Collection<? extends File> files,
                                                  Collection<? extends File> classpath,
                                                  Collection<? extends File> platformCp,
                                                  ModulePath modulePath,
                                                  Collection<? extends File> upgradeModulePath,
                                                  Collection<? extends File> sourcePath,
                                                  Map<File, Set<File>> outs,
                                                  final CanceledStatus canceledStatus) {
    //final long compileStart = System.nanoTime();
    //System.err.println("Compile start; since global start: " + TimeUnit.NANOSECONDS.toMillis(compileStart - myGlobalStart));
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
      public void registerJavacFileData(JavacFileData data) {
        customOutputData(JavacFileData.CUSTOM_DATA_PLUGIN_ID, JavacFileData.CUSTOM_DATA_KIND, data.asBytes());
      }

      @Override
      public void customOutputData(String pluginId, String dataName, byte[] data) {
        final JavacRemoteProto.Message.Response response = JavacProtoUtil.createCustomDataResponse(pluginId, dataName, data);
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
        options, files, classpath, platformCp, modulePath, upgradeModulePath, sourcePath, outs, diagnostic, outputSink, canceledStatus, tool
      );
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createBuildCompletedResponse(rc));
    }
    catch (Exception e) {
      //noinspection UseOfSystemOutOrSystemErr
      e.printStackTrace(System.err);
      return JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure(e.getMessage(), e));
    }
    //finally {
      //final long compileEnd = System.nanoTime();
      //System.err.println("Compiled in " + TimeUnit.NANOSECONDS.toMillis(compileEnd - compileStart) + " ms");
      //System.err.println("Compiled in " + TimeUnit.NANOSECONDS.toMillis(compileEnd - compileStart) + " ms; since global start: " + TimeUnit.NANOSECONDS.toMillis(compileEnd - myGlobalStart));
    //}
  }

  private static JavaCompilingTool getCompilingTool() {
    String property = System.getProperty(JPS_JAVA_COMPILING_TOOL_PROPERTY);
    if (property != null) {
      final ServiceLoader<JavaCompilingTool> loader = ServiceLoader.load(JavaCompilingTool.class, JavaCompilingTool.class.getClassLoader());
      for (JavaCompilingTool tool : loader) {
        if (property.equals(tool.getId()) || property.equals(tool.getAlternativeId())) {
          return tool;
        }
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
            if (myCanceled.putIfAbsent(sessionId, Boolean.FALSE) == null) { // if not running yet
              final List<String> options = request.getOptionList();
              final List<File> files = toFiles(request.getFileList());
              final List<File> cp = toFiles(request.getClasspathList());
              final List<File> platformCp = toFiles(request.getPlatformClasspathList());
              final List<File> srcPath = toFiles(request.getSourcepathList());

              final ModulePath.Builder modulePathBuilder = ModulePath.newBuilder();
              final Map<String, String> namesMap = request.getModuleNamesMap();
              for (String path : request.getModulePathList()) {
                modulePathBuilder.add(namesMap.get(path), new File(path));
              }
              final ModulePath modulePath = modulePathBuilder.create();

              final List<File> upgradeModulePath = toFiles(request.getUpgradeModulePathList());

              final Map<File, Set<File>> outs = new HashMap<File, Set<File>>();
              for (JavacRemoteProto.Message.Request.OutputGroup outputGroup : request.getOutputList()) {
                final Set<File> srcRoots = new HashSet<File>();
                for (String root : outputGroup.getSourceRootList()) {
                  srcRoots.add(new File(root));
                }
                outs.put(new File(outputGroup.getOutputRoot()), srcRoots);
              }
              myThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                  boolean keepRunning = myKeepRunning;
                  try {
                    final JavacRemoteProto.Message result = compile(context, sessionId, options, files, cp, platformCp, modulePath, upgradeModulePath, srcPath, outs, new CanceledStatus() {
                      @Override
                      public boolean isCanceled() {
                        return Boolean.TRUE.equals(myCanceled.get(sessionId));
                      }
                    });
                    context.channel().writeAndFlush(result).awaitUninterruptibly();
                  }
                  catch (Throwable throwable) {
                    // in case of unexpected exception exit, to ensure the process is not stuck in a problematic state
                    keepRunning = false;
                    throwable.printStackTrace(System.err);
                    try {
                      // attempt to report via proto
                      context.channel().writeAndFlush(JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure(throwable.getMessage(), throwable)));
                    }
                    catch (Throwable ignored) {
                    }
                  }
                  finally {
                    myCanceled.remove(sessionId); // state cleanup
                    if (keepRunning) {
                      JavacMain.clearCompilerZipFileCache();
                      //noinspection CallToSystemGC
                      System.gc();
                    }
                    else {
                      // in this mode this is only one-time compilation process that should stop after build is complete
                      ExternalJavacProcess.this.stop();
                    }
                    Thread.interrupted(); // reset interrupted status
                  }
                }
              });
            }
          }
          else if (requestType == JavacRemoteProto.Message.Request.Type.CANCEL){
            cancelBuild(sessionId);
          }
          else if (requestType == JavacRemoteProto.Message.Request.Type.SHUTDOWN){
            // cancel all running builds
            // todo: optionally wait for all builds to complete and only then shutdown
            for (UUID uuid : myCanceled.keySet()) {
              // todo: do we really need to wait for cancelled sessions to terminate?
              cancelBuild(uuid);
            }
            new Thread("StopThread") {
              @Override
              public void run() {
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
      //final long stopStart = System.nanoTime();
      //System.err.println("Exiting. Since global start " + TimeUnit.NANOSECONDS.toMillis(stopStart - myGlobalStart));
      final ChannelFuture future = myConnectFuture;
      if (future != null) {
        future.channel().close().await();
      }
      myEventLoopGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS).await();
      //final long stopEnd = System.nanoTime();
      //System.err.println("Stop completed in " + TimeUnit.NANOSECONDS.toMillis(stopEnd - stopStart) + "ms; since global start: " + TimeUnit.NANOSECONDS.toMillis(stopEnd - myGlobalStart));
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

  public void cancelBuild(UUID sessionId) {
    myCanceled.replace(sessionId, Boolean.FALSE, Boolean.TRUE);
  }

}