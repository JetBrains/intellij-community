// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.BaseOutputReader;
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
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.GlobalContextKey;

import javax.tools.Diagnostic;
import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ExternalJavacManager extends ProcessAdapter {
  private static final Logger LOG = Logger.getInstance(ExternalJavacManager.class);

  public static final GlobalContextKey<ExternalJavacManager> KEY = GlobalContextKey.create("_external_javac_server_");
  public static final int DEFAULT_SERVER_PORT = 7878;
  public static final String STDOUT_LINE_PREFIX = "JAVAC_PROCESS[STDOUT]";
  public static final String STDERR_LINE_PREFIX = "JAVAC_PROCESS[STDERR]";

  private static final AttributeKey<UUID> PROCESS_ID_KEY = AttributeKey.valueOf("ExternalJavacServer.ProcessId");
  private static final Key<Integer> PROCESS_HASH = Key.create("ExternalJavacServer.SdkHomePath");

  private final File myWorkingDir;
  private final ChannelRegistrar myChannelRegistrar;
  private int myListenPort = DEFAULT_SERVER_PORT;
  private final Map<UUID, CompileSession> mySessions = Collections.synchronizedMap(new HashMap<>());
  private final Map<UUID, ExternalJavacProcessHandler> myRunningProcesses = Collections.synchronizedMap(new HashMap<>());
  private final Map<UUID, Channel> myConnections = Collections.synchronizedMap(new HashMap<>()); // processId->channel
  private final Executor myExecutor;
  private boolean myOwnExecutor;
  private final long myKeepAliveTimeout;

  public ExternalJavacManager(@NotNull final File workingDir, @NotNull Executor executor) {
    this(workingDir, executor, 5 * 60 * 1000L /* 5 minutes default*/);
  }

  public ExternalJavacManager(@NotNull final File workingDir, @NotNull Executor executor, long keepAliveTimeout) {
    myWorkingDir = workingDir;
    myChannelRegistrar = new ChannelRegistrar();
    myExecutor = executor;
    myKeepAliveTimeout = keepAliveTimeout;
  }

  public void start(int listenPort) {
    final ChannelHandler compilationRequestsHandler = new CompilationRequestsHandler();
    final ServerBootstrap bootstrap = new ServerBootstrap()
      .group(new NioEventLoopGroup(1, myExecutor))
      .channel(NioServerSocketChannel.class)
      .childOption(ChannelOption.TCP_NODELAY, true)
      .childOption(ChannelOption.SO_KEEPALIVE, true)
      .childHandler(new ChannelInitializer() {
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
    myChannelRegistrar.add(bootstrap.bind(InetAddress.getLoopbackAddress(), listenPort).syncUninterruptibly().channel());
    myListenPort = listenPort;
  }


  public ExternalJavacRunResult forkJavac(String javaHome,
                                          int heapSize,
                                          Iterable<String> vmOptions,
                                          Iterable<String> options,
                                          CompilationPaths paths,
                                          Iterable<? extends File> files,
                                          Map<File, Set<File>> outs,
                                          DiagnosticOutputConsumer diagnosticSink,
                                          OutputFileConsumer outputSink,
                                          JavaCompilingTool compilingTool,
                                          CanceledStatus cancelStatus, final boolean keepProcessAlive) {
    try {
      final ExternalJavacProcessHandler running = findRunningProcess(processHash(javaHome, vmOptions, compilingTool));
      final ExternalJavacProcessHandler processHandler = running != null? running : launchExternalJavacProcess(
        javaHome, heapSize, myListenPort, myWorkingDir, vmOptions, compilingTool, keepProcessAlive
      );

      final Channel channel = lookupChannel(processHandler.getProcessId());
      if (channel != null) {
        final CompileSession session = new CompileSession(
          processHandler.getProcessId(), new ExternalJavacMessageHandler(diagnosticSink, outputSink, getEncodingName(options)), cancelStatus
        );
        mySessions.put(session.getId(), session);
        channel.writeAndFlush(JavacProtoUtil.toMessage(session.getId(), JavacProtoUtil.createCompilationRequest(
          options, files, paths.getClasspath(), paths.getPlatformClasspath(), paths.getModulePath(), paths.getUpgradeModulePath(), paths.getSourcePath(), outs
        )));
        return session;
      }
      else {
        LOG.warn("Failed to connect to javac process");
      }
    }
    catch (Throwable e) {
      LOG.info(e);
      diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    return ExternalJavacRunResult.FAILURE;
  }

  // returns true if all process handlers terminated
  @TestOnly
  public boolean waitForAllProcessHandlers(long time, @NotNull TimeUnit unit) {
    final Collection<ExternalJavacProcessHandler> processes;
    synchronized (myRunningProcesses) {
      processes = new ArrayList<>(myRunningProcesses.values());
    }
    for (ProcessHandler handler : processes) {
      if (!handler.waitFor(unit.toMillis(time))) {
        return false;
      }
    }
    return true;
  }

  @TestOnly
  public boolean awaitNettyThreadPoolTermination(long time, @NotNull TimeUnit unit) {
    if (myOwnExecutor && myExecutor instanceof ExecutorService) {
      try {
        return ((ExecutorService)myExecutor).awaitTermination(time, unit);
      }
      catch (InterruptedException ignored) {
      }
    }
    return true;
  }

  private ExternalJavacProcessHandler findRunningProcess(int processHash) {
    debug(()-> "findRunningProcess: looking for hash " + processHash);
    List<ExternalJavacProcessHandler> idleProcesses = null;
    try {
      synchronized (myRunningProcesses) {
        for (Map.Entry<UUID, ExternalJavacProcessHandler> entry : myRunningProcesses.entrySet()) {
          final ExternalJavacProcessHandler process = entry.getValue();
          if (!process.isKeepProcessAlive()) {
            //we shouldn't try to reuse process which will stop after finishing the current compilation
            continue;
          }

          final Integer hash = PROCESS_HASH.get(process);
          if (hash != null && hash == processHash && process.lock()) {
            debug(()-> "findRunningProcess: returning process " + process.getProcessId() + " for hash " + processHash);
            return process;
          }
          if (process.getIdleTime() > myKeepAliveTimeout) {
            if (idleProcesses == null) {
              idleProcesses = new ArrayList<>();
            }
            debug(()-> "findRunningProcess: adding " + process.getProcessId() + " to idle list");
            idleProcesses.add(process);
          }
        }
      }
      debug(()-> "findRunningProcess: no running process for " + processHash + " is found");
      return null;
    }
    finally {
      if (idleProcesses != null) {
        for (ExternalJavacProcessHandler process : idleProcesses) {
          shutdownProcess(process);
        }
      }
    }
  }

  private static <T> void debug(T data, Function<? super T, String> message) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(message.apply(data));
    }
  }

  private static void debug(Supplier<String> message) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(message.get());
    }
  }

  private static int processHash(String sdkHomePath, Iterable<String> vmOptions, JavaCompilingTool tool) {
    Collection<? extends String> opts = vmOptions instanceof Collection<?>? (Collection<String>)vmOptions : Iterators.collect(vmOptions, new ArrayList<>());
    return Objects.hash(sdkHomePath.replace(File.separatorChar, '/'), opts, tool.getId());
  }

  @Nullable
  private static String getEncodingName(Iterable<String> options) {
    for (Iterator<String> it = options.iterator(); it.hasNext(); ) {
      final String option = it.next();
      if ("-encoding".equals(option)) {
        return it.hasNext()? it.next() : null;
      }
    }
    return null;
  }

  public boolean isRunning() {
    return !myChannelRegistrar.isEmpty();
  }

  public void stop() {
    synchronized (myConnections) {
      for (Map.Entry<UUID, Channel> entry : myConnections.entrySet()) {
        entry.getValue().writeAndFlush(JavacProtoUtil.toMessage(entry.getKey(), JavacProtoUtil.createShutdownRequest()));
      }
    }
    myChannelRegistrar.close().awaitUninterruptibly();
    if (myOwnExecutor && myExecutor instanceof ExecutorService) {
      final ExecutorService service = (ExecutorService)myExecutor;
      service.shutdown();
      try {
        service.awaitTermination(15, TimeUnit.SECONDS);
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  public void shutdownIdleProcesses() {
    List<ExternalJavacProcessHandler> idleProcesses = null;
    synchronized (myRunningProcesses) {
      for (ExternalJavacProcessHandler process : myRunningProcesses.values()) {
        final long idle = process.getIdleTime();
        if (idle > myKeepAliveTimeout) {
          if (idleProcesses == null) {
            idleProcesses = new ArrayList<>();
          }
          idleProcesses.add(process);
        }
      }
    }
    if (idleProcesses != null) {
      for (ExternalJavacProcessHandler process : idleProcesses) {
        shutdownProcess(process);
      }
    }
  }

  private boolean shutdownProcess(ExternalJavacProcessHandler process) {
    UUID processId = process.getProcessId();
    debug(()-> "shutdownProcess: shutting down " + processId);
    final Channel conn = myConnections.get(processId);
    if (conn != null && process.lock()) {
      debug(()-> "shutdownProcess: sending shutdown request to " + processId);
      conn.writeAndFlush(JavacProtoUtil.toMessage(processId, JavacProtoUtil.createShutdownRequest()));
      return true;
    }
    return false;
  }

  private ExternalJavacProcessHandler launchExternalJavacProcess(String sdkHomePath,
                                                                 int heapSize,
                                                                 int port,
                                                                 File workingDir,
                                                                 Iterable<String> vmOptions,
                                                                 JavaCompilingTool compilingTool,
                                                                 final boolean keepProcessAlive) throws Exception {
    final UUID processId = UUID.randomUUID();
    final List<String> cmdLine = new ArrayList<>();

    appendParam(cmdLine, getVMExecutablePath(sdkHomePath));

    appendParam(cmdLine, "-Djava.awt.headless=true");

    //appendParam(cmdLine, "-XX:MaxPermSize=150m");
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
    copyProperty(cmdLine, "file.encoding");
    copyProperty(cmdLine, "user.language");
    copyProperty(cmdLine, "user.country");
    copyProperty(cmdLine, "user.region");

    copyProperty(cmdLine, "io.netty.noUnsafe");

    appendParam(cmdLine, "-D" + ExternalJavacProcess.JPS_JAVA_COMPILING_TOOL_PROPERTY + "=" + compilingTool.getId());

    // this will disable standard extensions to ensure javac is loaded from the right tools.jar
    appendParam(cmdLine, "-Djava.ext.dirs=");

    for (String option : vmOptions) {
      appendParam(cmdLine, option);
    }

    appendParam(cmdLine, "-classpath");
    List<File> cp = ClasspathBootstrap.getExternalJavacProcessClasspath(sdkHomePath, compilingTool);
    appendParam(cmdLine, cp.stream().map(File::getPath).collect(Collectors.joining(File.pathSeparator)));

    appendParam(cmdLine, ExternalJavacProcess.class.getName());
    appendParam(cmdLine, processId.toString());
    appendParam(cmdLine, InetAddress.getLoopbackAddress().getHostAddress());
    appendParam(cmdLine, Integer.toString(port));
    appendParam(cmdLine, Boolean.toString(keepProcessAlive));  // keep in memory after build finished

    appendParam(cmdLine, FileUtil.toSystemIndependentName(workingDir.getPath()));

    debug(()-> "starting external compiler: " + cmdLine);
    FileUtil.createDirectory(workingDir);

    final int processHash = processHash(sdkHomePath, vmOptions, compilingTool);
    final ExternalJavacProcessHandler processHandler = createProcessHandler(processId, new ProcessBuilder(cmdLine).directory(workingDir).start(), StringUtil.join(cmdLine, " "), keepProcessAlive);
    PROCESS_HASH.set(processHandler, processHash);
    processHandler.lock();
    myRunningProcesses.put(processId, processHandler);
    debug(()-> "external compiler process registered: id=" + processId + ", hash=" + processHash);
    processHandler.addProcessListener(this);
    processHandler.startNotify();
    return processHandler;
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    final UUID processId = ((ExternalJavacProcessHandler)event.getProcessHandler()).getProcessId();
    debug(()-> "process " + processId + " terminated");
    myRunningProcesses.remove(processId);
    if (myConnections.get(processId) == null) {
      // only if connection has never been established
      // otherwise we rely on the fact that ExternalJavacProcess always closes connection before shutdown and clean sessions on ChannelUnregister event
      cleanSessions(processId);
    }
  }

  private void cleanSessions(UUID processId) {
    synchronized (mySessions) {
      for (Iterator<Map.Entry<UUID, CompileSession>> it = mySessions.entrySet().iterator(); it.hasNext(); ) {
        final CompileSession session = it.next().getValue();
        if (processId.equals(session.getProcessId())) {
          session.setDone();
          it.remove();
        }
      }
    }
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    final String text = event.getText();
    if (!StringUtil.isEmptyOrSpaces(text)) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("text from javac: " + text);
      }
      String prefix = null;
      if (outputType == ProcessOutputTypes.STDOUT) {
        prefix = STDOUT_LINE_PREFIX;
      }
      else if (outputType == ProcessOutputTypes.STDERR) {
        prefix = STDERR_LINE_PREFIX;
      }
      if (prefix != null) {
        List<DiagnosticOutputConsumer> consumers = null;
        final UUID processId = ((ExternalJavacProcessHandler)event.getProcessHandler()).getProcessId();
        synchronized (mySessions) {
          for (CompileSession session : mySessions.values()) {
            if (processId.equals(session.getProcessId())) {
              if (consumers == null) {
                consumers = new ArrayList<>();
              }
              consumers.add(session.myHandler.getDiagnosticSink());
            }
          }
        }
        final String msg = prefix + ": " + text;
        if (consumers != null) {
          for (DiagnosticOutputConsumer consumer : consumers) {
            consumer.outputLineAvailable(msg);
          }
        }
        else {
          LOG.info(msg.trim());
        }
      }
    }
  }


  protected ExternalJavacProcessHandler createProcessHandler(UUID processId, @NotNull Process process, @NotNull String commandLine, boolean keepProcessAlive) {
    return new ExternalJavacProcessHandler(processId, process, commandLine, keepProcessAlive);
  }

  private static void appendParam(List<? super String> cmdLine, String parameter) {
    if (SystemInfo.isWindows) {
      if (parameter.contains("\"")) {
        parameter = StringUtil.replace(parameter, "\"", "\\\"");
      }
      else if (parameter.length() == 0) {
        parameter = "\"\"";
      }
    }
    cmdLine.add(parameter);
  }

  private static void copyProperty(List<? super String> cmdLine, String name) {
    String value = System.getProperty(name);
    if (value != null) {
      appendParam(cmdLine, "-D" + name + '=' + value);
    }
  }

  private static String getVMExecutablePath(String sdkHome) {
    return sdkHome + "/bin/java";
  }

  protected static class ExternalJavacProcessHandler extends BaseOSProcessHandler {
    private long myIdleSince;
    private final UUID myProcessId;
    private final boolean myKeepProcessAlive;
    private boolean myIsBusy;

    protected ExternalJavacProcessHandler(UUID processId, @NotNull Process process, @NotNull String commandLine, boolean keepProcessAlive) {
      super(process, commandLine, null);
      myProcessId = processId;
      myKeepProcessAlive = keepProcessAlive;
    }

    public UUID getProcessId() {
      return myProcessId;
    }

    public synchronized long getIdleTime() {
      final long idleSince = myIdleSince;
      return idleSince == -42L? 0L : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - idleSince);
    }

    public synchronized void unlock() {
      myIdleSince = System.nanoTime();
      myIsBusy = false;
    }

    public synchronized boolean lock() {
      myIdleSince = -42L;
      return !myIsBusy && (myIsBusy = true);
    }

    public boolean isKeepProcessAlive() {
      return myKeepProcessAlive;
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      // if keepAlive requested, that means the process will be waiting considerable periods of time without any output
      return myKeepProcessAlive? BaseOutputReader.Options.BLOCKING : super.readerOptions();
    }
  }

  @ChannelHandler.Sharable
  private class CompilationRequestsHandler extends SimpleChannelInboundHandler<JavacRemoteProto.Message> {
    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
      try {
        final Channel channel = context.channel();
        final UUID processId = channel.attr(PROCESS_ID_KEY).get();
        if (processId != null) {
          cleanSessions(processId);
          myConnections.remove(processId);
        }
      }
      finally {
        super.channelInactive(context);
      }
    }

    @Override
    public void channelRead0(final ChannelHandlerContext context, JavacRemoteProto.Message message) throws Exception {
      // in case of REQUEST_ACK this is a process ID, otherwise this is a sessionId
      final UUID msgUuid = JavacProtoUtil.fromProtoUUID(message.getSessionId());

      CompileSession session = mySessions.get(msgUuid);
      final ExternalJavacMessageHandler handler = session != null? session.myHandler : null;
      final JavacRemoteProto.Message.Type messageType = message.getMessageType();

      JavacRemoteProto.Message reply = null;
      try {
        if (messageType == JavacRemoteProto.Message.Type.RESPONSE) {
          final JavacRemoteProto.Message.Response response = message.getResponse();
          final JavacRemoteProto.Message.Response.Type responseType = response.getResponseType();
          if (responseType == JavacRemoteProto.Message.Response.Type.REQUEST_ACK) {
            // in this case msgUuid is a process ID, so we need to save the channel, associated with the process
            final Channel channel = context.channel();
            channel.attr(PROCESS_ID_KEY).set(msgUuid);
            synchronized (myConnections) {
              myConnections.put(msgUuid, channel);
              myConnections.notifyAll();
            }
            return;
          }
        }

        if (handler != null) {
          final boolean terminateOk = handler.handleMessage(message);
          if (terminateOk) {
            session.setDone();
            mySessions.remove(session.getId());
            final ExternalJavacProcessHandler process = myRunningProcesses.get(session.getProcessId());
            if (process != null) {
              process.unlock();
            }
          }
          else if (session.isCancelRequested()) {
            reply = JavacProtoUtil.toMessage(msgUuid, JavacProtoUtil.createCancelRequest());
          }
        }
        else {
          LOG.info("No message handler is registered to handle message " + messageType.name() + "; canceling the process");
          reply = JavacProtoUtil.toMessage(msgUuid, JavacProtoUtil.createCancelRequest());
        }
      }
      finally {
        if (reply != null) {
          context.channel().writeAndFlush(reply);
        }
      }
    }
  }

  private Channel lookupChannel(UUID processId) {
    Channel channel = null;
    synchronized (myConnections) {
      channel = myConnections.get(processId);
      debug(channel, ch-> "lookupChannel: channel for " + processId + " is " + ch);
      while (channel == null) {
        if (!myRunningProcesses.containsKey(processId)) {
          debug(()-> "lookupChannel: no process for " + processId);
          break; // the process is already gone
        }
        try {
          myConnections.wait(300L);
        }
        catch (InterruptedException ignored) {
        }
        channel = myConnections.get(processId);
        debug(channel, ch-> "lookupChannel: after wait channel for " + processId + " is " + ch);
      }
    }
    return channel;
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
      try {
        return openChannels.close();
      }
      finally {
        if (eventLoopGroup != null) {
          eventLoopGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS);
        }
      }
    }
  }

  private class CompileSession extends ExternalJavacRunResult{
    private final UUID myId;
    private final UUID myProcessId;
    private final CanceledStatus myCancelStatus;
    private final ExternalJavacMessageHandler myHandler;
    private final Semaphore myDone = new Semaphore();

    CompileSession(@NotNull UUID processId, @NotNull ExternalJavacMessageHandler handler, CanceledStatus cancelStatus) {
      myProcessId = processId;
      myCancelStatus = cancelStatus;
      myId = UUID.randomUUID();
      myHandler = handler;
      myDone.down();
    }

    @NotNull
    public UUID getId() {
      return myId;
    }

    @NotNull
    public UUID getProcessId() {
      return myProcessId;
    }

    @Override
    public boolean isDone() {
      return myDone.isUp();
    }

    public void setDone() {
      myDone.up();
    }

    public boolean isTerminatedSuccessfully() {
      return myHandler.isTerminatedSuccessfully();
    }

    boolean isCancelRequested() {
      return myCancelStatus.isCanceled();
    }

    @NotNull
    @Override
    public Boolean get() {
      while (true) {
        try {
          if (myDone.waitForUnsafe(300L)) {
            break;
          }
        }
        catch (InterruptedException ignored) {
        }
        notifyCancelled();
      }
      boolean successfully = isTerminatedSuccessfully();
      if (!successfully) {
        debug(()-> "Javac compile session " + myId + " in process " + myProcessId + "didn't terminate successfully");
      }
      return successfully;
    }

    @NotNull
    @Override
    public Boolean get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, TimeoutException {
      if (!myDone.waitForUnsafe(unit.toMillis(timeout))) {
        notifyCancelled();
        // if execution continues, just notify about timeout
        throw new TimeoutException();
      }
      boolean successfully = isTerminatedSuccessfully();
      if (!successfully) {
        debug(()-> "Javac compile session " + myId + " in process " + myProcessId + "didn't terminate successfully");
      }
      return successfully;
    }

    private void notifyCancelled() {
      if (isCancelRequested() && myRunningProcesses.containsKey(myProcessId)) {
        final Channel channel = myConnections.get(myProcessId);
        if (channel != null) {
          channel.writeAndFlush(JavacProtoUtil.toMessage(myId, JavacProtoUtil.createCancelRequest()));
        }
      }
    }

  }
}