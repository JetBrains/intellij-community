/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
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
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.Diagnostic;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12                       
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ExternalJavacManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.javac.ExternalJavacServer");
  public static final GlobalContextKey<ExternalJavacManager> KEY = GlobalContextKey.create("_external_javac_server_");
  
  public static final int DEFAULT_SERVER_PORT = 7878;
  public static final String STDOUT_LINE_PREFIX = "JAVAC_PROCESS[STDOUT]";
  public static final String STDERR_LINE_PREFIX = "JAVAC_PROCESS[STDERR]";
  private static final AttributeKey<JavacProcessDescriptor> SESSION_DESCRIPTOR = AttributeKey.valueOf("ExternalJavacServer.JavacProcessDescriptor");
  @NotNull
  private final File myWorkingDir;
  @NotNull
  private final ChannelRegistrar myChannelRegistrar;
  private final Map<UUID, JavacProcessDescriptor> myMessageHandlers = new HashMap<UUID, JavacProcessDescriptor>();
  private int myListenPort = DEFAULT_SERVER_PORT;

  public ExternalJavacManager(@NotNull final File workingDir) {
    myWorkingDir = workingDir;
    myChannelRegistrar = new ChannelRegistrar();
  }

  @NotNull
  public File getWorkingDir() {
    return myWorkingDir;
  }

  public void start(int listenPort) {
    final ServerBootstrap bootstrap = new ServerBootstrap().group(new NioEventLoopGroup(1, SharedThreadPool.getInstance())).channel(NioServerSocketChannel.class);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
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
    try {
      final InetAddress loopback = InetAddress.getByName(null);
      myChannelRegistrar.add(bootstrap.bind(loopback, listenPort).syncUninterruptibly().channel());
      myListenPort = listenPort;
    }
    catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }
  

  public boolean forkJavac(final String javaHome, final int heapSize, List<String> vmOptions, List<String> options,
                           Collection<File> platformCp,
                           Collection<File> classpath,
                           Collection<File> modulePath,
                           Collection<File> sourcePath,
                           Collection<File> files,
                           Map<File, Set<File>> outs,
                           final DiagnosticOutputConsumer diagnosticSink, OutputFileConsumer outputSink,
                           final JavaCompilingTool compilingTool,
                           final CanceledStatus cancelStatus) {
    final ExternalJavacMessageHandler rh = new ExternalJavacMessageHandler(diagnosticSink, outputSink, getEncodingName(options));
    final JavacRemoteProto.Message.Request request = JavacProtoUtil.createCompilationRequest(options, files, classpath, platformCp, modulePath, sourcePath, outs);
    final UUID uuid = UUID.randomUUID();
    final JavacProcessDescriptor processDescriptor = new JavacProcessDescriptor(uuid, rh, request);
    synchronized (myMessageHandlers) {
      myMessageHandlers.put(uuid, processDescriptor);
    }
    try {
      final ExternalJavacProcessHandler processHandler = launchExternalJavacProcess(
        uuid, javaHome, heapSize, myListenPort, myWorkingDir, vmOptions, compilingTool
      );
      processHandler.addProcessListener(new ProcessAdapter() {
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          final String text = event.getText();
          if (!StringUtil.isEmptyOrSpaces(text)) {
            String prefix = null;
            if (outputType == ProcessOutputTypes.STDOUT) {
              prefix = STDOUT_LINE_PREFIX;
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
              prefix = STDERR_LINE_PREFIX;
            }
            if (prefix != null) {
              diagnosticSink.outputLineAvailable(prefix + ": " + text);
            }
          }
        }
      });
      processHandler.startNotify();

      while (!processDescriptor.waitFor(300L)) {
        if (processHandler.isProcessTerminated() && processDescriptor.channel == null && processHandler.getExitCode() != 0) {
          // process terminated abnormally and no communication took place
          processDescriptor.setDone();
          break;
        }
        if (cancelStatus.isCanceled()) {
          processDescriptor.cancelBuild();
        }
      }

      return rh.isTerminatedSuccessfully();
    }
    catch (Throwable e) {
      LOG.info(e);
      diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    finally {
      unregisterMessageHandler(uuid);
    }
    return false;
  }

  private void unregisterMessageHandler(UUID uuid) {
    final JavacProcessDescriptor descriptor;
    synchronized (myMessageHandlers) {
      descriptor = myMessageHandlers.remove(uuid);
    }
    if (descriptor != null) {
      descriptor.setDone();
    }
  }

  @Nullable
  private static String getEncodingName(List<String> options) {
    boolean found = false;
    for (String option : options) {
      if (found) {
        return option;
      }
      if ("-encoding".equalsIgnoreCase(option)) {
        found = true;
      }
    }
    return null;
  }
  
  public void stop() {
    myChannelRegistrar.close().awaitUninterruptibly();
  }

  private ExternalJavacProcessHandler launchExternalJavacProcess(UUID uuid, String sdkHomePath,
                                                                        int heapSize,
                                                                        int port,
                                                                        File workingDir,
                                                                        List<String> vmOptions,
                                                                        JavaCompilingTool compilingTool) throws Exception {
    final List<String> cmdLine = new ArrayList<String>();
    appendParam(cmdLine, getVMExecutablePath(sdkHomePath));
    //appendParam(cmdLine, "-XX:MaxPermSize=150m");
    //appendParam(cmdLine, "-XX:ReservedCodeCacheSize=64m");
    appendParam(cmdLine, "-Djava.awt.headless=true");
    if (heapSize > 0) {
      // if the value is zero or negative, use JVM default memory settings
      final int xms = heapSize / 2;
      if (xms > 32) {
        appendParam(cmdLine, "-Xms" + xms + "m");
      }
      appendParam(cmdLine, "-Xmx" + heapSize + "m");
    }

    // debugging
    //appendParam(cmdLine, "-XX:+HeapDumpOnOutOfMemoryError");
    //appendParam(cmdLine, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5009");

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    final String encoding = System.getProperty("file.encoding");
    if (encoding != null) {
      appendParam(cmdLine, "-Dfile.encoding=" + encoding);
    }
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      appendParam(cmdLine, "-Duser.language=" + lang);
    }
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      appendParam(cmdLine, "-Duser.country=" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      appendParam(cmdLine, "-Duser.region=" + region);
    }

    appendParam(cmdLine, "-D" + ExternalJavacProcess.JPS_JAVA_COMPILING_TOOL_PROPERTY + "=" + compilingTool.getId());

    // this will disable standard extensions to ensure javac is loaded from the right tools.jar
    appendParam(cmdLine, "-Djava.ext.dirs=");

    appendParam(cmdLine, "-Dlog4j.defaultInitOverride=true");

    for (String option : vmOptions) {
      appendParam(cmdLine, option);
    }

    appendParam(cmdLine, "-classpath");

    final List<File> cp = ClasspathBootstrap.getExternalJavacProcessClasspath(sdkHomePath, compilingTool);
    final StringBuilder classpath = new StringBuilder();
    for (File file : cp) {
      if (classpath.length() > 0) {
        classpath.append(File.pathSeparator);
      }
      classpath.append(file.getPath());
    }
    appendParam(cmdLine, classpath.toString());

    appendParam(cmdLine, ExternalJavacProcess.class.getName());
    appendParam(cmdLine, uuid.toString());
    appendParam(cmdLine, "127.0.0.1");
    appendParam(cmdLine, Integer.toString(port));

    workingDir.mkdirs();

    appendParam(cmdLine, FileUtil.toSystemIndependentName(workingDir.getPath()));

    final ProcessBuilder builder = new ProcessBuilder(cmdLine);
    builder.directory(workingDir);

    final Process process = builder.start();
    return createProcessHandler(process, StringUtil.join(cmdLine, " "));
  }

  protected ExternalJavacProcessHandler createProcessHandler(@NotNull Process process, @NotNull String commandLine) {
    return new ExternalJavacProcessHandler(process, commandLine);
  }

  private static void appendParam(List<String> cmdLine, String param) {
    if (SystemInfo.isWindows) {
      if (param.contains("\"")) {
        param = StringUtil.replace(param, "\"", "\\\"");
      }
      else if (param.length() == 0) {
        param = "\"\"";
      }
    }
    cmdLine.add(param);
  }

  private static String getVMExecutablePath(String sdkHome) {
    return sdkHome + "/bin/java";
  }

  protected static class ExternalJavacProcessHandler extends BaseOSProcessHandler {
    private volatile int myExitCode;

    protected ExternalJavacProcessHandler(@NotNull Process process, @NotNull String commandLine) {
      super(process, commandLine, null);
      addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(ProcessEvent event) {
          myExitCode = event.getExitCode();
        }
      });
    }

    public int getExitCode() {
      return myExitCode;
    }
  }

  @ChannelHandler.Sharable
  private class CompilationRequestsHandler extends SimpleChannelInboundHandler<JavacRemoteProto.Message> {
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
      JavacProcessDescriptor descriptor = ctx.channel().attr(SESSION_DESCRIPTOR).getAndRemove();
      if (descriptor != null) {
        descriptor.setDone();
      }
      super.channelUnregistered(ctx);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext context, JavacRemoteProto.Message message) throws Exception {
      JavacProcessDescriptor descriptor = context.channel().attr(SESSION_DESCRIPTOR).get();
  
      UUID sessionId;
      if (descriptor == null) {
        // this is the first message for this session, so fill session data with missing info
        sessionId = JavacProtoUtil.fromProtoUUID(message.getSessionId());
  
        descriptor = myMessageHandlers.get(sessionId);
        if (descriptor != null) {
          descriptor.channel = context.channel();
          context.channel().attr(SESSION_DESCRIPTOR).set(descriptor);
        }
      }
      else {
        sessionId = descriptor.sessionId;
      }
  
      final ExternalJavacMessageHandler handler = descriptor != null? descriptor.handler : null;

      final JavacRemoteProto.Message.Type messageType = message.getMessageType();

      JavacRemoteProto.Message reply = null;
      try {
        if (messageType == JavacRemoteProto.Message.Type.RESPONSE) {
          final JavacRemoteProto.Message.Response response = message.getResponse();
          final JavacRemoteProto.Message.Response.Type responseType = response.getResponseType();
          if (handler != null) {
            if (responseType == JavacRemoteProto.Message.Response.Type.REQUEST_ACK) {
              final JavacRemoteProto.Message.Request request = descriptor.request;
              if (request != null) {
                reply = JavacProtoUtil.toMessage(sessionId, request);
                descriptor.request = null;
              }
            }
            else {
              final boolean terminateOk = handler.handleMessage(message);
              if (terminateOk) {
                descriptor.setDone();
              }
            }
          }
          else {
            reply = JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createCancelRequest());
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
        eventLoopGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS);
      }
      return future;
    }
  }

  private static class JavacProcessDescriptor {
    @NotNull
    final UUID sessionId;
    @NotNull
    final ExternalJavacMessageHandler handler;
    volatile JavacRemoteProto.Message.Request request;
    volatile Channel channel;
    private final Semaphore myDone = new Semaphore();

    public JavacProcessDescriptor(@NotNull UUID sessionId, @NotNull ExternalJavacMessageHandler handler, @NotNull JavacRemoteProto.Message.Request request) {
      this.sessionId = sessionId;
      this.handler = handler;
      this.request = request;
      myDone.down();
    }

    public void cancelBuild() {
      if (channel != null) {
        channel.writeAndFlush(JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createCancelRequest()));
      }
    }
    
    public void setDone() {
      myDone.up();
    }
    
    
    public boolean waitFor(long timeout) {
      return myDone.waitFor(timeout);
    }
    
  }
  
}