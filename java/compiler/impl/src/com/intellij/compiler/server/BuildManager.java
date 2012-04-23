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
package com.intellij.compiler.server;

import com.intellij.ProjectTopics;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.server.impl.CompileServerClasspathManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.notification.Notification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.jetbrains.jps.server.Server;

import javax.tools.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class BuildManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildManager");
  private static final String SYSTEM_ROOT = "compile-server";
  private static final String LOGGER_CONFIG = "log.xml";
  private static final String DEFAULT_LOGGER_CONFIG = "defaultLogConfig.xml";
  private final File mySystemDirectory;
  private final ProjectManager myProjectManager;
  private static final int MAKE_TRIGGER_DELAY = 5 * 1000 /*5 seconds*/;
  private final Map<RequestFuture, Project> myAutomakeFutures = new HashMap<RequestFuture, Project>();
  private final CompileServerClasspathManager myClasspathManager = new CompileServerClasspathManager();
  private final Executor myPooledThreadExecutor = new Executor() {
    @Override
    public void execute(Runnable command) {
      ApplicationManager.getApplication().executeOnPooledThread(command);
    }
  };
  private final Map<String, SequentialTaskExecutor> myProjectToExecutorMap = Collections.synchronizedMap(new HashMap<String, SequentialTaskExecutor>());

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("compile-manager");
  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();
  private int myListenPort = -1;
  private volatile CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings myGlobals;

  public BuildManager(final ProjectManager projectManager) {
    myProjectManager = projectManager;
    final String systemPath = PathManager.getSystemPath();
    File system = new File(systemPath);
    try {
      system = system.getCanonicalFile();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    mySystemDirectory = system;

    projectManager.addProjectManagerListener(new ProjectWatcher());
    final MessageBusConnection conn = ApplicationManager.getApplication().getMessageBus().connect();
    conn.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
      private final AtomicBoolean myAutoMakeInProgress = new AtomicBoolean(false);
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (shouldTriggerMake(events)) {
          scheduleMake(new Runnable() {
            @Override
            public void run() {
              if (!myAutoMakeInProgress.getAndSet(true)) {
                try {
                  ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
                    @Override
                    public void run() {
                      try {
                        runAutoMake();
                      }
                      finally {
                        myAutoMakeInProgress.set(false);
                      }
                    }
                  });
                }
                catch (RejectedExecutionException ignored) {
                  // we were shut down
                }
              }
              else {
                scheduleMake(this);
              }
            }
          });
        }
      }

      private void scheduleMake(Runnable runnable) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(runnable, MAKE_TRIGGER_DELAY);
      }

      private boolean shouldTriggerMake(List<? extends VFileEvent> events) {
        if (CompileDriver.OUT_OF_PROCESS_MAKE_AS_SERVER) {
          return false;
        }
        for (VFileEvent event : events) {
          if (event.isFromRefresh() || event.getRequestor() instanceof SavingRequestor) {
            return true;
          }
        }
        return false;
      }
    });

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        stopListening();
      }
    });
  }

  public static BuildManager getInstance() {
    return ApplicationManager.getApplication().getComponent(BuildManager.class);
  }

  public void notifyFilesChanged(Collection<String> paths) {
    // todo: update state
  }

  public void notifyFilesDeleted(Collection<String> paths) {
    // todo: update state
  }

  @Nullable
  private static String getProjectPath(final Project project) {
    final String path = project.getPresentableUrl();
    if (path == null) {
      return null;
    }
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(path);
    return vFile != null ? vFile.getPath() : null;
  }

  private void clearGlobalsCache() {
    myGlobals = null;
  }

  private void runAutoMake() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    final Project[] openProjects = myProjectManager.getOpenProjects();
    if (openProjects.length > 0) {
      final List<RequestFuture> futures = new ArrayList<RequestFuture>();
      for (final Project project : openProjects) {
        if (project.isDefault() || project.isDisposed()) {
          continue;
        }
        final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
        if (!config.useCompileServer() || !config.MAKE_PROJECT_ON_SAVE) {
          continue;
        }
        final List<String> emptyList = Collections.emptyList();
        final RequestFuture future = scheduleBuild(
          project, false, true, emptyList, emptyList, emptyList, Collections.<String, String>emptyMap(),
          new AutoMakeMessageHandler(project)
        );
        if (future != null) {
          futures.add(future);
          synchronized (myAutomakeFutures) {
            myAutomakeFutures.put(future, project);
          }
        }
      }
      try {
        for (RequestFuture future : futures) {
          future.waitFor();
        }
      }
      finally {
        synchronized (myAutomakeFutures) {
          myAutomakeFutures.keySet().removeAll(futures);
        }
      }
    }
  }

  public void cancelAutoMakeTasks(Project project) {
    synchronized (myAutomakeFutures) {
      for (Map.Entry<RequestFuture, Project> entry : myAutomakeFutures.entrySet()) {
        if (entry.getValue().equals(project)) {
          entry.getKey().cancel(false);
        }
      }
    }
  }


  // todo: avoid FS scan on every start

  @Nullable
  public RequestFuture scheduleBuild(
    final Project project, final boolean isRebuild,
    final boolean isMake,
    final Collection<String> modules,
    final Collection<String> artifacts,
    final Collection<String> paths,
    final Map<String, String> userData, final BuildMessageHandler handler) {

    final String projectPath = getProjectPath(project);
    final UUID sessionId = UUID.randomUUID();
    final CmdlineRemoteProto.Message.ControllerMessage params;
    CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals = myGlobals;
    if (globals == null) {
      globals = buildGlobalSettings();
      myGlobals = globals;
    }
    if (isRebuild) {
      params = CmdlineProtoUtil.createRebuildRequest(projectPath, userData, globals);
    }
    else {
      params = isMake ?
               CmdlineProtoUtil.createMakeRequest(projectPath, modules, artifacts, userData, globals) :
               CmdlineProtoUtil.createForceCompileRequest(projectPath, modules, artifacts, paths, userData, globals);
    }

    myMessageDispatcher.registerBuildMessageHandler(sessionId, handler, params);

    // ensure server is listening
    if (myListenPort < 0) {
      try {
        myListenPort = startListening();
      }
      catch (Exception e) {
        myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
        handler.handleFailure(CmdlineProtoUtil.createFailure(e.getMessage(), null));
        handler.sessionTerminated();
        return null;
      }
    }

    final RequestFuture<BuildMessageHandler> future = new RequestFuture<BuildMessageHandler>(handler, sessionId, new RequestFuture.CancelAction<com.intellij.compiler.server.BuildMessageHandler>() {
      @Override
      public void cancel(RequestFuture<BuildMessageHandler> future) throws Exception {
        myMessageDispatcher.cancelSession(future.getRequestID());
      }
    });

    SequentialTaskExecutor sequential;
    synchronized (myProjectToExecutorMap) {
      sequential = myProjectToExecutorMap.get(projectPath);
      if (sequential == null) {
        sequential = new SequentialTaskExecutor(myPooledThreadExecutor);
        myProjectToExecutorMap.put(projectPath, sequential);
      }
    }

    sequential.submit(new Runnable() {
      @Override
      public void run() {
        try {
          if (project.isDisposed()) {
            future.cancel(false);
            return;
          }
          final Process process = launchBuildProcess(myListenPort, sessionId);
          final OSProcessHandler processHandler = new OSProcessHandler(process, null) {
            @Override
            protected boolean shouldDestroyProcessRecursively() {
              return true;
            }
          };
          processHandler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(ProcessEvent event) {
              final BuildMessageHandler handler = myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
              if (handler != null) {
                handler.sessionTerminated();
              }
            }

            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
              // re-translate builder's output to idea.log
              final String text = event.getText();
              if (!StringUtil.isEmpty(text)) {
                LOG.info("BUILDER_PROCESS [" +outputType.toString() +"]: "+ text.trim());
              }
            }
          });
          processHandler.startNotify();
          processHandler.waitFor();
        }
        catch (ExecutionException e) {
          myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
          handler.handleFailure(CmdlineProtoUtil.createFailure(e.getMessage(), e));
          handler.sessionTerminated();
        }
        finally {
          future.setDone();
        }
      }
    });

    return future;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    stopListening();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "com.intellij.compiler.server.BuildManager";
  }

  private static CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings buildGlobalSettings() {
    final Map<String, String> data = new HashMap<String, String>();

    for (Map.Entry<String, String> entry : PathMacrosImpl.getGlobalSystemMacros().entrySet()) {
      data.put(entry.getKey(), FileUtil.toSystemIndependentName(entry.getValue()));
    }

    final PathMacros pathVars = PathMacros.getInstance();
    for (String name : pathVars.getAllMacroNames()) {
      final String path = pathVars.getValue(name);
      if (path != null) {
        data.put(name, FileUtil.toSystemIndependentName(path));
      }
    }

    final List<GlobalLibrary> globals = new ArrayList<GlobalLibrary>();

    fillSdks(globals);
    fillGlobalLibraries(globals);

    final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.Builder cmdBuilder =
      CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.newBuilder();

    if (!data.isEmpty()) {
      for (Map.Entry<String, String> entry : data.entrySet()) {
        final String var = entry.getKey();
        final String value = entry.getValue();
        if (var != null && value != null) {
          cmdBuilder.addPathVariable(CmdlineProtoUtil.createPair(var, value));
        }
      }
    }

    if (!globals.isEmpty()) {
      for (GlobalLibrary lib : globals) {
        final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.GlobalLibrary.Builder libBuilder =
          CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.GlobalLibrary.newBuilder();
        libBuilder.setName(lib.getName()).addAllPath(lib.getPaths());
        if (lib instanceof SdkLibrary) {
          final SdkLibrary sdk = (SdkLibrary)lib;
          libBuilder.setHomePath(sdk.getHomePath());
          libBuilder.setTypeName(sdk.getTypeName());
          final String additional = sdk.getAdditionalDataXml();
          if (additional != null) {
            libBuilder.setAdditionalDataXml(additional);
          }
          final String version = sdk.getVersion();
          if (version != null) {
            libBuilder.setVersion(version);
          }
        }
        cmdBuilder.addGlobalLibrary(libBuilder.build());
      }
    }

    final String defaultCharset = EncodingManager.getInstance().getDefaultCharsetName();
    if (defaultCharset != null) {
      cmdBuilder.setGlobalEncoding(defaultCharset);
    }

    final String ignoredFilesList = FileTypeManager.getInstance().getIgnoredFilesList();
    cmdBuilder.setIgnoredFilesPatterns(ignoredFilesList);
    return cmdBuilder.build();
  }

  private static void fillSdks(List<GlobalLibrary> globals) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final String name = sdk.getName();
      final String homePath = sdk.getHomePath();
      if (homePath == null) {
        continue;
      }
      final SdkAdditionalData data = sdk.getSdkAdditionalData();
      final String additionalDataXml;
      final SdkType sdkType = sdk.getSdkType();
      if (data == null) {
        additionalDataXml = null;
      }
      else {
        final Element element = new Element("additional");
        sdkType.saveAdditionalData(data, element);
        additionalDataXml = JDOMUtil.writeElement(element, "\n");
      }
      final List<String> paths = convertToLocalPaths(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
      String versionString = sdk.getVersionString();
      if (versionString != null && sdkType instanceof JavaSdk) {
        final JavaSdkVersion version = ((JavaSdk)sdkType).getVersion(versionString);
        if (version != null) {
          versionString = version.getDescription();
        }
      }
      globals.add(new SdkLibrary(name, sdkType.getName(), versionString, homePath, paths, additionalDataXml));
    }
  }

  private static void fillGlobalLibraries(List<GlobalLibrary> globals) {
    final Iterator<Library> iterator = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryIterator();
    while (iterator.hasNext()) {
      Library library = iterator.next();
      final String name = library.getName();

      if (name != null) {
        final List<String> paths = convertToLocalPaths(library.getFiles(OrderRootType.CLASSES));
        globals.add(new GlobalLibrary(name, paths));
      }
    }
  }

  private static List<String> convertToLocalPaths(VirtualFile[] files) {
    final List<String> paths = new ArrayList<String>();
    for (VirtualFile file : files) {
      if (file.isValid()) {
        paths.add(StringUtil.trimEnd(FileUtil.toSystemIndependentName(file.getPath()), JarFileSystem.JAR_SEPARATOR));
      }
    }
    return paths;
  }

  private Process launchBuildProcess(final int port, final UUID sessionId) throws ExecutionException {
    // todo: add code for choosing the jdk with which to run
    // validate tools.jar presence
    final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
    if (systemCompiler == null) {
      throw new ExecutionException("No system java compiler is provided by the JRE. Make sure tools.jar is present in IntelliJ IDEA classpath.");
    }

    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    final String vmExecutablePath = ((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk);
    cmdLine.setExePath(vmExecutablePath);
    cmdLine.addParameter("-server");
    cmdLine.addParameter("-XX:MaxPermSize=150m");
    cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");
    cmdLine.addParameter("-Xmx" + Registry.intValue("compiler.server.heap.size") + "m");
    cmdLine.addParameter("-Djava.awt.headless=true");

    final String shouldGenerateIndex = System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION);
    if (shouldGenerateIndex != null) {
      cmdLine.addParameter("-D"+ GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION +"=" + shouldGenerateIndex);
    }

    final String additionalOptions = Registry.stringValue("compiler.server.vm.options");
    if (!StringUtil.isEmpty(additionalOptions)) {
      final StringTokenizer tokenizer = new StringTokenizer(additionalOptions, " ", false);
      while (tokenizer.hasMoreTokens()) {
        cmdLine.addParameter(tokenizer.nextToken());
      }
    }

    // debugging
    final int debugPort = Registry.intValue("compiler.server.debug.port");
    if (debugPort > 0) {
      cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
      cmdLine.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
    }

    if (Registry.is("compiler.server.use.memory.temp.cache")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION);
    }
    if (Registry.is("compiler.server.use.external.javac.process")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_EXTERNAL_JAVAC_OPTION);
    }
    final String host = NetUtils.getLocalHostString();
    cmdLine.addParameter("-D"+ GlobalOptions.HOSTNAME_OPTION + "=" + host);

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      cmdLine.addParameter("-Duser.language=" + lang);
    }
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      cmdLine.addParameter("-Duser.country=" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      cmdLine.addParameter("-Duser.region=" + region);
    }

    cmdLine.addParameter("-classpath");

    final List<File> cp = ClasspathBootstrap.getCompileServerApplicationClasspath();
    cp.addAll(myClasspathManager.getCompileServerPluginsClasspath());

    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter(BuildMain.class.getName());
    cmdLine.addParameter(host);
    cmdLine.addParameter(Integer.toString(port));
    cmdLine.addParameter(sessionId.toString());

    final File workDirectory = new File(mySystemDirectory, SYSTEM_ROOT);
    workDirectory.mkdirs();
    ensureLogConfigExists(workDirectory);

    cmdLine.addParameter(FileUtil.toSystemIndependentName(workDirectory.getPath()));

    cmdLine.setWorkDirectory(workDirectory);

    return cmdLine.createProcess();
  }

  private static void ensureLogConfigExists(File workDirectory) {
    final File logConfig = new File(workDirectory, LOGGER_CONFIG);
    if (!logConfig.exists()) {
      FileUtil.createIfDoesntExist(logConfig);
      try {
        final InputStream in = Server.class.getResourceAsStream("/" + DEFAULT_LOGGER_CONFIG);
        if (in != null) {
          try {
            final FileOutputStream out = new FileOutputStream(logConfig);
            try {
              FileUtil.copy(in, out);
            }
            finally {
              out.close();
            }
          }
          finally {
            in.close();
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public void stopListening() {
    final ChannelGroupFuture closeFuture = myAllOpenChannels.close();
    closeFuture.awaitUninterruptibly();
  }

  private int startListening() throws Exception {
    final ChannelFactory channelFactory = new NioServerSocketChannelFactory(myPooledThreadExecutor, myPooledThreadExecutor);
    final SimpleChannelUpstreamHandler channelRegistrar = new SimpleChannelUpstreamHandler() {
      public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        myAllOpenChannels.add(e.getChannel());
        super.channelOpen(ctx, e);
      }

      @Override
      public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        myAllOpenChannels.remove(e.getChannel());
        super.channelClosed(ctx, e);
      }
    };
    ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          channelRegistrar,
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(CmdlineRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          myMessageDispatcher
        );
      }
    };
    final ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
    bootstrap.setPipelineFactory(pipelineFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    final int listenPort = NetUtils.findAvailableSocketPort();
    final Channel serverChannel = bootstrap.bind(new InetSocketAddress(listenPort));
    myAllOpenChannels.add(serverChannel);
    return listenPort;
  }

  private static String classpathToString(List<File> cp) {
    StringBuilder builder = new StringBuilder();
    for (File file : cp) {
      if (builder.length() > 0) {
        builder.append(File.pathSeparator);
      }
      builder.append(FileUtil.toCanonicalPath(file.getPath()));
    }
    return builder.toString();
  }

  private static class AutoMakeMessageHandler extends BuildMessageHandler {
    private CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status myBuildStatus;
    private final Project myProject;
    private final WolfTheProblemSolver myWolf;

    public AutoMakeMessageHandler(Project project) {
      myProject = project;
      myBuildStatus = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.SUCCESS;
      myWolf = WolfTheProblemSolver.getInstance(project);
    }

    @Override
    protected void handleBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event) {
      if (myProject.isDisposed()) {
        return;
      }
      switch (event.getEventType()) {
        case BUILD_COMPLETED:
          if (event.hasCompletionStatus()) {
            myBuildStatus = event.getCompletionStatus();
          }
          return;

        case FILES_GENERATED:
          final CompilationStatusListener publisher = myProject.getMessageBus().syncPublisher(CompilerTopics.COMPILATION_STATUS);
          for (CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile generatedFile : event.getGeneratedFilesList()) {
            final String root = FileUtil.toSystemIndependentName(generatedFile.getOutputRoot());
            final String relativePath = FileUtil.toSystemIndependentName(generatedFile.getRelativePath());
            publisher.fileGenerated(root, relativePath);
          }
          return;

        default:
          return;
      }
    }

    @Override
    protected void handleCompileMessage(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
      if (myProject.isDisposed()) {
        return;
      }
      final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind kind = message.getKind();
      if (kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.ERROR) {
        informWolf(myProject, message);
      }
    }

    @Override
    public void handleFailure(CmdlineRemoteProto.Message.Failure failure) {
      CompilerManager.NOTIFICATION_GROUP.createNotification("Auto make failure: " + failure.getDescription(), MessageType.INFO);
    }

    @Override
    public void sessionTerminated() {
      String statusMessage = null/*"Auto make completed"*/;
      switch (myBuildStatus) {
        case SUCCESS:
          //statusMessage = "Auto make completed successfully";
          break;
        case UP_TO_DATE:
          //statusMessage = "All files are up-to-date";
          break;
        case ERRORS:
          statusMessage = "Auto make completed with errors";
          break;
        case CANCELED:
          //statusMessage = "Auto make has been canceled";
          break;
      }
      if (statusMessage != null) {
        final Notification notification = CompilerManager.NOTIFICATION_GROUP.createNotification(statusMessage, MessageType.INFO);
        if (!myProject.isDisposed()) {
          notification.notify(myProject);
        }
      }
    }

    private void informWolf(Project project, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
      final String srcPath = message.getSourceFilePath();
      if (srcPath != null && !project.isDisposed()) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(srcPath);
        if (vFile != null) {
          final int line = (int)message.getLine();
          final int column = (int)message.getColumn();
          if (line > 0 && column > 0) {
            final Problem problem = myWolf.convertToProblem(vFile, line, column, new String[]{message.getText()});
            myWolf.weHaveGotProblems(vFile, Collections.singletonList(problem));
          }
          else {
            myWolf.queue(vFile);
          }
        }
      }
    }
  }

  private class ProjectWatcher extends ProjectManagerAdapter {
    private final Map<Project, MessageBusConnection> myConnections = new HashMap<Project, MessageBusConnection>();

    @Override
    public void projectOpened(final Project project) {
      final MessageBusConnection conn = project.getMessageBus().connect();
      myConnections.put(project, conn);
      conn.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        @Override
        public void beforeRootsChange(final ModuleRootEvent event) {
        }

        @Override
        public void rootsChanged(final ModuleRootEvent event) {
          clearGlobalsCache();
        }
      });
    }

    @Override
    public void projectClosing(Project project) {
    }

    @Override
    public void projectClosed(Project project) {
      myProjectToExecutorMap.remove(getProjectPath(project));
      final MessageBusConnection conn = myConnections.remove(project);
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  private static class BuildMessageDispatcher extends SimpleChannelHandler {
    private final Map<UUID, SessionData> myMessageHandlers = new ConcurrentHashMap<UUID, SessionData>();
    private final Set<UUID> myCanceledSessions = new ConcurrentHashSet<UUID>();

    public void registerBuildMessageHandler(UUID sessionId,
                                            BuildMessageHandler handler,
                                            CmdlineRemoteProto.Message.ControllerMessage params) {
      myMessageHandlers.put(sessionId, new SessionData(sessionId, handler, params));
    }

    @Nullable
    public BuildMessageHandler unregisterBuildMessageHandler(UUID sessionId) {
      myCanceledSessions.remove(sessionId);
      final SessionData data = myMessageHandlers.remove(sessionId);
      return data != null? data.handler : null;
    }

    public void cancelSession(UUID sessionId) {
      if (myCanceledSessions.add(sessionId)) {
        final SessionData data = myMessageHandlers.get(sessionId);
        if (data != null) {
          final Channel channel = data.channel;
          if (channel != null) {
            Channels.write(channel, CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
          }
        }
      }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      final CmdlineRemoteProto.Message message = (CmdlineRemoteProto.Message)e.getMessage();

      SessionData sessionData = (SessionData)ctx.getAttachment();

      UUID sessionId;
      if (sessionData == null) {
        // this is the first message for this session, so fill session data with missing info
        final CmdlineRemoteProto.Message.UUID id = message.getSessionId();
        sessionId = new UUID(id.getMostSigBits(), id.getLeastSigBits());

        sessionData = myMessageHandlers.get(sessionId);
        if (sessionData != null) {
          sessionData.channel = ctx.getChannel();
          ctx.setAttachment(sessionData);
        }
        if (myCanceledSessions.contains(sessionId)) {
          Channels.write(ctx.getChannel(), CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
        }
      }
      else {
        sessionId = sessionData.sessionId;
      }

      final BuildMessageHandler handler = sessionData != null? sessionData.handler : null;
      if (handler == null) {
        // todo
        LOG.info("No message handler registered for session " + sessionId);
        return;
      }

      final CmdlineRemoteProto.Message.Type messageType = message.getType();
      switch (messageType) {
        case FAILURE:
          handler.handleFailure(message.getFailure());
          break;

        case BUILDER_MESSAGE:
          final CmdlineRemoteProto.Message.BuilderMessage builderMessage = message.getBuilderMessage();
          final CmdlineRemoteProto.Message.BuilderMessage.Type responseType = builderMessage.getType();
          if (responseType == CmdlineRemoteProto.Message.BuilderMessage.Type.PARAM_REQUEST) {
            final CmdlineRemoteProto.Message.ControllerMessage params = sessionData.params;
            if (params != null) {
              sessionData.params = null;
              Channels.write(ctx.getChannel(), CmdlineProtoUtil.toMessage(sessionId, params));
            }
            else {
              cancelSession(sessionId);
            }
          }
          else {
            handler.handleBuildMessage(builderMessage);
          }
          break;

        default:
          LOG.info("Unsupported message type " + messageType);
          break;
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      try {
        super.channelClosed(ctx, e);
      }
      finally {
        final SessionData sessionData = (SessionData)ctx.getAttachment();
        if (sessionData != null) {
          final BuildMessageHandler handler = unregisterBuildMessageHandler(sessionData.sessionId);
          if (handler != null) {
            // notify the handler only if it has not been notified yet
            handler.sessionTerminated();
          }
        }
      }
    }


    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      try {
        super.channelDisconnected(ctx, e);
      }
      finally {
        final SessionData sessionData = (SessionData)ctx.getAttachment();
        if (sessionData != null) { // this is the context corresponding to some session
          final Channel channel = e.getChannel();
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              channel.close();
            }
          });
        }
      }
    }
  }


  private static final class SessionData {
    final UUID sessionId;
    final BuildMessageHandler handler;
    volatile CmdlineRemoteProto.Message.ControllerMessage params;
    volatile Channel channel;

    private SessionData(UUID sessionId, BuildMessageHandler handler, CmdlineRemoteProto.Message.ControllerMessage params) {
      this.sessionId = sessionId;
      this.handler = handler;
      this.params = params;
    }
  }

}
