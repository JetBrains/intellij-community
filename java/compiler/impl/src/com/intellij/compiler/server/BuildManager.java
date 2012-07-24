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
import com.intellij.compiler.server.impl.CompileServerClasspathManager;
import com.intellij.execution.ExecutionAdapter;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
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
import org.jetbrains.jps.cmdline.ClasspathBootstrap;

import javax.tools.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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
  private static final int MAKE_TRIGGER_DELAY = 3 * 1000 + 500/*3.5 seconds*/;
  private final boolean IS_UNIT_TEST_MODE;

  private final File mySystemDirectory;
  private final ProjectManager myProjectManager;

  private final Map<RequestFuture, Project> myAutomakeFutures = new HashMap<RequestFuture, Project>();
  private final Map<String, RequestFuture> myBuildsInProgress = Collections.synchronizedMap(new HashMap<String, RequestFuture>());
  private final CompileServerClasspathManager myClasspathManager = new CompileServerClasspathManager();
  private final Executor myPooledThreadExecutor = new Executor() {
    @Override
    public void execute(Runnable command) {
      ApplicationManager.getApplication().executeOnPooledThread(command);
    }
  };
  private final SequentialTaskExecutor myRequestsProcessor = new SequentialTaskExecutor(myPooledThreadExecutor);
  private final Map<String, ProjectData> myProjectDataMap = Collections.synchronizedMap(new HashMap<String, ProjectData>());

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicBoolean myAutoMakeInProgress = new AtomicBoolean(false);

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("build-manager");
  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();
  private int myListenPort = -1;
  private volatile CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings myGlobals;

  public BuildManager(final ProjectManager projectManager) {
    IS_UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();
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
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (shouldTriggerMake(events)) {
          scheduleAutoMake();
        }
      }

      private boolean shouldTriggerMake(List<? extends VFileEvent> events) {
        if (!PowerSaveMode.isEnabled()) {
          for (VFileEvent event : events) {
            if (event.isFromRefresh() || event.getRequestor() instanceof SavingRequestor) {
              return true;
            }
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

  public void notifyFilesChanged(final Collection<String> paths) {
    doNotify(paths, false);
  }

  public void notifyFilesDeleted(Collection<String> paths) {
    doNotify(paths, true);
  }

  private void doNotify(final Collection<String> paths, final boolean notifyDeletion) {
    // ensure events processed in the order they arrived
    myRequestsProcessor.submit(new Runnable() {
      @Override
      public void run() {
        synchronized (myProjectDataMap) {
          if (IS_UNIT_TEST_MODE) {
            if (notifyDeletion) {
              LOG.info("Registering deleted paths: " + paths);
            }
            else {
              LOG.info("Registering changed paths: " + paths);
            }
          }
          for (Map.Entry<String, ProjectData> entry : myProjectDataMap.entrySet()) {
            final ProjectData data = entry.getValue();
            if (notifyDeletion) {
              data.addDeleted(paths);
            }
            else {
              data.addChanged(paths);
            }
            final RequestFuture future = myBuildsInProgress.get(entry.getKey());
            if (future != null && !future.isCancelled() && !future.isDone()) {
              final UUID sessionId = future.getRequestID();
              final Channel channel = myMessageDispatcher.getConnectedChannel(sessionId);
              if (channel != null) {
                final CmdlineRemoteProto.Message.ControllerMessage message =
                  CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(
                    CmdlineRemoteProto.Message.ControllerMessage.Type.FS_EVENT).setFsEvent(data.createNextEvent()).build();
                Channels.write(channel, CmdlineProtoUtil.toMessage(sessionId, message));
              }
            }
          }
        }
      }
    });
  }

  public void clearState(Project project) {
    myGlobals = null;
    final String projectPath = getProjectPath(project);
    synchronized (myProjectDataMap) {
      final ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null) {
        data.dropChanges();
      }
    }
  }

  public boolean rescanRequired(Project project) {
    final String projectPath = getProjectPath(project);
    synchronized (myProjectDataMap) {
      final ProjectData data = myProjectDataMap.get(projectPath);
      return data == null || data.myNeedRescan;
    }
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

  private void scheduleAutoMake() {
    if (IS_UNIT_TEST_MODE || PowerSaveMode.isEnabled()) {
      return;
    }
    addMakeRequest(new Runnable() {
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
          addMakeRequest(this);
        }
      }
    });
  }

  private void addMakeRequest(Runnable runnable) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(runnable, MAKE_TRIGGER_DELAY);
  }

  private void runAutoMake() {
    final Project[] openProjects = myProjectManager.getOpenProjects();
    if (openProjects.length > 0) {
      final List<RequestFuture> futures = new ArrayList<RequestFuture>();
      for (final Project project : openProjects) {
        if (project.isDefault() || project.isDisposed()) {
          continue;
        }
        final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
        if (!config.useOutOfProcessBuild() || !config.MAKE_PROJECT_ON_SAVE) {
          continue;
        }
        if (!config.allowAutoMakeWhileRunningApplication()) {
          final RunContentManager contentManager = ExecutionManager.getInstance(project).getContentManager();
          boolean hasRunningProcesses = false;
          for (RunContentDescriptor descriptor : contentManager.getAllDescriptors()) {
            final ProcessHandler handler = descriptor.getProcessHandler();
            if (handler != null && !handler.isProcessTerminated()) { // active process
              hasRunningProcesses = true;
              break;
            }
          }
          if (hasRunningProcesses) {
            continue;
          }
        }

        final List<String> emptyList = Collections.emptyList();
        final RequestFuture future = scheduleBuild(
          project, false, true, emptyList, emptyList, emptyList, Collections.<String, String>emptyMap(), new AutoMakeMessageHandler(project)
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

  public Collection<RequestFuture> cancelAutoMakeTasks(Project project) {
    final Collection<RequestFuture> futures = new ArrayList<RequestFuture>();
    synchronized (myAutomakeFutures) {
      for (Map.Entry<RequestFuture, Project> entry : myAutomakeFutures.entrySet()) {
        if (entry.getValue().equals(project)) {
          final RequestFuture future = entry.getKey();
          future.cancel(false);
          futures.add(future);
        }
      }
    }
    return futures;
  }

  @Nullable
  public RequestFuture scheduleBuild(
    final Project project, final boolean isRebuild,
    final boolean isMake,
    final Collection<String> modules,
    final Collection<String> artifacts,
    final Collection<String> paths,
    final Map<String, String> userData, final DefaultMessageHandler handler) {

    final String projectPath = getProjectPath(project);
    final UUID sessionId = UUID.randomUUID();

    // ensure server is listening
    if (myListenPort < 0) {
      try {
        myListenPort = startListening();
      }
      catch (Exception e) {
        handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), null));
        handler.sessionTerminated();
        return null;
      }
    }

    try {
      final RequestFuture<BuilderMessageHandler> future = new RequestFuture<BuilderMessageHandler>(handler, sessionId, new RequestFuture.CancelAction<BuilderMessageHandler>() {
        @Override
        public void cancel(RequestFuture<BuilderMessageHandler> future) throws Exception {
          myMessageDispatcher.cancelSession(future.getRequestID());
        }
      });
      // by using the same queue that processes events we ensure that
      // the build will be aware of all events that have happened before this request
      myRequestsProcessor.submit(new Runnable() {
        @Override
        public void run() {
          if (future.isCancelled() || project.isDisposed()) {
            handler.sessionTerminated();
            future.setDone();
            return;
          }

          CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals = myGlobals;
          if (globals == null) {
            globals = buildGlobalSettings();
            myGlobals = globals;
          }
          CmdlineRemoteProto.Message.ControllerMessage.FSEvent currentFSChanges = null;
          final SequentialTaskExecutor projectTaskQueue;
          synchronized (myProjectDataMap) {
            ProjectData data = myProjectDataMap.get(projectPath);
            if (data == null) {
              data = new ProjectData(new SequentialTaskExecutor(myPooledThreadExecutor));
              myProjectDataMap.put(projectPath, data);
            }
            if (isRebuild) {
              data.dropChanges();
            }
            if (IS_UNIT_TEST_MODE) {
              LOG.info("Scheduling build for " + projectPath + "; CHANGED: " + new HashSet<String>(data.myChanged) + "; DELETED: " + new HashSet<String>(data.myDeleted));
            }
            currentFSChanges = data.getAndResetRescanFlag() ? null : data.createNextEvent();
            projectTaskQueue = data.taskQueue;
          }

          final CmdlineRemoteProto.Message.ControllerMessage params;
          if (isRebuild) {
            params = CmdlineProtoUtil.createRebuildRequest(projectPath, userData, globals);
          }
          else {
            params = isMake ?
                     CmdlineProtoUtil.createMakeRequest(projectPath, modules, artifacts, userData, globals, currentFSChanges) :
                     CmdlineProtoUtil.createForceCompileRequest(projectPath, modules, artifacts, paths, userData, globals, currentFSChanges);
          }

          myMessageDispatcher.registerBuildMessageHandler(sessionId, handler, params);

          try {
            projectTaskQueue.submit(new Runnable() {
              @Override
              public void run() {
                try {
                  if (project.isDisposed()) {
                    myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
                    handler.sessionTerminated();
                    return;
                  }
                  myBuildsInProgress.put(projectPath, future);
                  final Process process = launchBuildProcess(project, myListenPort, sessionId);
                  final OSProcessHandler processHandler = new OSProcessHandler(process, null) {
                    @Override
                    protected boolean shouldDestroyProcessRecursively() {
                      return true;
                    }
                  };
                  final StringBuilder stdErrOutput = new StringBuilder();
                  processHandler.addProcessListener(new ProcessAdapter() {
                    @Override
                    public void processTerminated(ProcessEvent event) {
                      final BuilderMessageHandler handler = myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
                      if (handler != null) {
                        handler.sessionTerminated();
                      }
                    }

                    @Override
                    public void onTextAvailable(ProcessEvent event, Key outputType) {
                      // re-translate builder's output to idea.log
                      final String text = event.getText();
                      if (!StringUtil.isEmptyOrSpaces(text)) {
                        LOG.info("BUILDER_PROCESS [" + outputType.toString() + "]: " + text.trim());
                        if (stdErrOutput.length() < 1024 && ProcessOutputTypes.STDERR.equals(outputType)) {
                          stdErrOutput.append(text);
                        }
                      }
                    }
                  });
                  processHandler.startNotify();
                  final boolean terminated = processHandler.waitFor();
                  if (terminated) {
                    final int exitValue = processHandler.getProcess().exitValue();
                    if (exitValue != 0) {
                      final StringBuilder msg = new StringBuilder();
                      msg.append("Abnormal build process termination: ");
                      if (stdErrOutput.length() > 0) {
                        msg.append("\n").append(stdErrOutput);
                      }
                      else {
                        msg.append("unknown error");
                      }
                      future.getMessageHandler().handleFailure(sessionId, CmdlineProtoUtil.createFailure(msg.toString(), null));
                    }
                  }
                  else {
                    future.getMessageHandler().handleFailure(sessionId, CmdlineProtoUtil.createFailure("Disconnected from build process", null));
                  }
                }
                catch (ExecutionException e) {
                  myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
                  handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
                  handler.sessionTerminated();
                }
                finally {
                  myBuildsInProgress.remove(projectPath);
                  future.setDone();
                }
              }
            });
          }
          catch (Throwable e) {
            myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
            handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
            handler.sessionTerminated();
            future.setDone();
          }
        }
      });

      return future;
    }
    catch (Throwable e) {
      handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
      handler.sessionTerminated();
    }

    return null;
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
    if (!StringUtil.isEmpty(defaultCharset)) {
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
      final SdkType sdkType = (SdkType) sdk.getSdkType();
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
    final LibraryTablesRegistrar tableRegistrar = LibraryTablesRegistrar.getInstance();
    List<LibraryTable> tables = new ArrayList<LibraryTable>();
    tables.add(tableRegistrar.getLibraryTable());

    tables.addAll(tableRegistrar.getCustomLibraryTables());
    for (LibraryTable libraryTable : tables) {
      final Iterator<Library> iterator = libraryTable.getLibraryIterator();
      while (iterator.hasNext()) {
        Library library = iterator.next();
        final String name = library.getName();

        if (name != null) {
          final List<String> paths = convertToLocalPaths(library.getFiles(OrderRootType.CLASSES));
          globals.add(new GlobalLibrary(name, paths));
        }
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

  private Process launchBuildProcess(Project project, final int port, final UUID sessionId) throws ExecutionException {
    // choosing sdk with which the build process should be run
    Sdk projectJdk = null;
    JavaSdkVersion sdkVersion = null;
    int sdkMinorVersion = 0;

    final Set<Sdk> candidates = new HashSet<Sdk>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdk) {
        candidates.add(sdk);
      }
    }
    // now select the latest version from the sdks that are used in the project, but not older than the internal sdk version
    for (Sdk candidate : candidates) {
      final String vs = candidate.getVersionString();
      if (vs != null) {
        final JavaSdkVersion candidateVersion = ((JavaSdk)candidate.getSdkType()).getVersion(vs);
        if (candidateVersion != null) {
          final int candidateMinorVersion = getMinorVersion(vs);
          if (projectJdk == null) {
            sdkVersion = candidateVersion;
            sdkMinorVersion = candidateMinorVersion;
            projectJdk = candidate;
          }
          else {
            final int result = candidateVersion.compareTo(sdkVersion);
            if (result > 0 || (result == 0 && candidateMinorVersion > sdkMinorVersion)) {
              sdkVersion = candidateVersion;
              sdkMinorVersion = candidateMinorVersion;
              projectJdk = candidate;
            }
          }
        }
      }
    }

    final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    if (projectJdk == null || sdkVersion == null || !sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_6)) {
      projectJdk = internalJdk;
    }

    // validate tools.jar presence
    final File compilerPath;
    if (projectJdk.equals(internalJdk)) {
      final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
      if (systemCompiler == null) {
        throw new ExecutionException("No system java compiler is provided by the JRE. Make sure tools.jar is present in IntelliJ IDEA classpath.");
      }
      compilerPath = ClasspathBootstrap.getResourcePath(systemCompiler.getClass());
    }
    else {
      final String path = ((JavaSdk)projectJdk.getSdkType()).getToolsPath(projectJdk);
      if (path == null) {
        throw new ExecutionException("Cannot determine path to 'tools.jar' library for " + projectJdk.getName() + " (" + projectJdk.getHomePath() + ")");
      }
      compilerPath = new File(path);
    }

    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    final String vmExecutablePath = ((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk);
    cmdLine.setExePath(vmExecutablePath);
    cmdLine.addParameter("-XX:MaxPermSize=150m");
    cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");
    final int heapSize = Registry.intValue("compiler.process.heap.size");
    final int xms = heapSize / 2;
    if (xms > 32) {
      cmdLine.addParameter("-Xms" + xms + "m");
    }
    cmdLine.addParameter("-Xmx" + heapSize + "m");

    if (SystemInfo.isMac && sdkVersion != null && JavaSdkVersion.JDK_1_6.equals(sdkVersion) && Registry.is("compiler.process.32bit.vm.on.mac")) {
      // unfortunately -d32 is supported on jdk 1.6 only
      cmdLine.addParameter("-d32");
    }

    cmdLine.addParameter("-Djava.awt.headless=true");
    if (IS_UNIT_TEST_MODE) {
      cmdLine.addParameter("-Dtest.mode=true");
    }
    cmdLine.addParameter("-Djdt.compiler.useSingleThread=true"); // always run eclipse compiler in single-threaded mode

    final String shouldGenerateIndex = System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION);
    if (shouldGenerateIndex != null) {
      cmdLine.addParameter("-D"+ GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION +"=" + shouldGenerateIndex);
    }

    final String additionalOptions = Registry.stringValue("compiler.process.vm.options");
    if (!StringUtil.isEmpty(additionalOptions)) {
      final StringTokenizer tokenizer = new StringTokenizer(additionalOptions, " ", false);
      while (tokenizer.hasMoreTokens()) {
        cmdLine.addParameter(tokenizer.nextToken());
      }
    }

    // debugging
    final int debugPort = Registry.intValue("compiler.process.debug.port");
    if (debugPort > 0) {
      cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
      cmdLine.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
    }

    if (Registry.is("compiler.process.use.memory.temp.cache")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION);
    }
    if (Registry.is("compiler.process.use.external.javac")) {
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

    final List<File> cp = ClasspathBootstrap.getBuildProcessApplicationClasspath();
    cp.add(compilerPath);
    cp.addAll(myClasspathManager.getCompileServerPluginsClasspath());

    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter(BuildMain.class.getName());
    cmdLine.addParameter(host);
    cmdLine.addParameter(Integer.toString(port));
    cmdLine.addParameter(sessionId.toString());

    final File workDirectory = getBuildSystemDirectory();
    workDirectory.mkdirs();
    ensureLogConfigExists(workDirectory);

    cmdLine.addParameter(FileUtil.toSystemIndependentName(workDirectory.getPath()));

    cmdLine.setWorkDirectory(workDirectory);

    return cmdLine.createProcess();
  }

  public File getBuildSystemDirectory() {
    return new File(mySystemDirectory, SYSTEM_ROOT);
  }

  private static int getMinorVersion(String vs) {
    final int dashIndex = vs.lastIndexOf('_');
    if (dashIndex >= 0) {
      StringBuilder builder = new StringBuilder();
      for (int idx = dashIndex + 1; idx < vs.length(); idx++) {
        final char ch = vs.charAt(idx);
        if (Character.isDigit(ch)) {
          builder.append(ch);
        }
        else {
          break;
        }
      }
      if (builder.length() > 0) {
        try {
          return Integer.parseInt(builder.toString());
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
    return 0;
  }

  private static void ensureLogConfigExists(File workDirectory) {
    final File logConfig = new File(workDirectory, LOGGER_CONFIG);
    if (!logConfig.exists()) {
      FileUtil.createIfDoesntExist(logConfig);
      try {
        final InputStream in = BuildMain.class.getResourceAsStream("/" + DEFAULT_LOGGER_CONFIG);
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

  private class ProjectWatcher extends ProjectManagerAdapter {
    private final Map<Project, MessageBusConnection> myConnections = new HashMap<Project, MessageBusConnection>();

    @Override
    public void projectOpened(final Project project) {
      final MessageBusConnection conn = project.getMessageBus().connect();
      myConnections.put(project, conn);
      conn.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
        @Override
        public void rootsChanged(final ModuleRootEvent event) {
          final Object source = event.getSource();
          if (source instanceof Project) {
            clearState((Project)source);
          }
        }
      });
      conn.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionAdapter() {
        @Override
        public void processTerminated(@NotNull RunProfile runProfile, @NotNull ProcessHandler handler) {
          scheduleAutoMake();
        }
      });
      scheduleAutoMake(); // run automake on project opening
    }

    @Override
    public boolean canCloseProject(Project project) {
      cancelAutoMakeTasks(project);
      return super.canCloseProject(project);
    }

    @Override
    public void projectClosing(Project project) {
      for (RequestFuture future : cancelAutoMakeTasks(project)) {
        future.waitFor(500, TimeUnit.MILLISECONDS);
      }
    }

    @Override
    public void projectClosed(Project project) {
      myProjectDataMap.remove(getProjectPath(project));
      final MessageBusConnection conn = myConnections.remove(project);
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  private static class ProjectData {
    final SequentialTaskExecutor taskQueue;
    private final Set<String> myChanged = new THashSet<String>(PathHashingStrategy.INSTANCE);
    private final Set<String> myDeleted = new THashSet<String>(PathHashingStrategy.INSTANCE);
    private long myNextEventOrdinal = 0L;
    private boolean myNeedRescan = true;

    private ProjectData(SequentialTaskExecutor taskQueue) {
      this.taskQueue = taskQueue;
    }

    public void addChanged(Collection<String> paths) {
      if (!myNeedRescan) {
        myDeleted.removeAll(paths);
        myChanged.addAll(paths);
      }
    }

    public void addDeleted(Collection<String> paths) {
      if (!myNeedRescan) {
        myChanged.removeAll(paths);
        myDeleted.addAll(paths);
      }
    }

    public CmdlineRemoteProto.Message.ControllerMessage.FSEvent createNextEvent() {
      final CmdlineRemoteProto.Message.ControllerMessage.FSEvent.Builder builder =
        CmdlineRemoteProto.Message.ControllerMessage.FSEvent.newBuilder();
      builder.setOrdinal(++myNextEventOrdinal);
      builder.addAllChangedPaths(myChanged);
      myChanged.clear();
      builder.addAllDeletedPaths(myDeleted);
      myDeleted.clear();
      return builder.build();
    }

    public boolean getAndResetRescanFlag() {
      final boolean rescan = myNeedRescan;
      myNeedRescan = false;
      return rescan;
    }

    public void dropChanges() {
      myNeedRescan = true;
      myNextEventOrdinal = 0L;
      myChanged.clear();
      myDeleted.clear();
    }

    static class PathHashingStrategy implements TObjectHashingStrategy<String> {
      static final PathHashingStrategy INSTANCE = new PathHashingStrategy();

      @Override
      public int computeHashCode(String path) {
        return FileUtil.pathHashCode(path);
      }

      @Override
      public boolean equals(String path1, String path2) {
        return FileUtil.pathsEqual(path1, path2);
      }
    }
  }

}
