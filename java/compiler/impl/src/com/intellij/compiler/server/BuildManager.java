/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.impl.CompileServerClasspathManager;
import com.intellij.execution.ExecutionAdapter;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import gnu.trove.THashSet;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.api.RequestFuture;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;

import javax.tools.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class BuildManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildManager");
  private static final String COMPILER_PROCESS_JDK_PROPERTY = "compiler.process.jdk";
  private static final String SYSTEM_ROOT = "compile-server";
  private static final String LOGGER_CONFIG = "log.xml";
  private static final String DEFAULT_LOGGER_CONFIG = "defaultLogConfig.xml";
  private static final int MAKE_TRIGGER_DELAY = 300 /*300 ms*/;
  private static final int DOCUMENT_SAVE_TRIGGER_DELAY = 1500 /*1.5 sec*/;
  private final boolean IS_UNIT_TEST_MODE;
  private static final String IWS_EXTENSION = ".iws";
  private static final String IPR_EXTENSION = ".ipr";
  private static final String IDEA_PROJECT_DIR_PATTERN = "/.idea/";
  private static final Function<String, Boolean> PATH_FILTER =
    SystemInfo.isFileSystemCaseSensitive?
    new Function<String, Boolean>() {
      @Override
      public Boolean fun(String s) {
        return !(s.contains(IDEA_PROJECT_DIR_PATTERN) || s.endsWith(IWS_EXTENSION) || s.endsWith(IPR_EXTENSION));
      }
    } :
    new Function<String, Boolean>() {
      @Override
      public Boolean fun(String s) {
        return !(StringUtil.endsWithIgnoreCase(s, IWS_EXTENSION) || StringUtil.endsWithIgnoreCase(s, IPR_EXTENSION) || StringUtil.containsIgnoreCase(s, IDEA_PROJECT_DIR_PATTERN));
      }
    };

  private final File mySystemDirectory;
  private final ProjectManager myProjectManager;

  private final Map<RequestFuture, Project> myAutomakeFutures = new HashMap<RequestFuture, Project>();
  private final Map<String, RequestFuture> myBuildsInProgress = Collections.synchronizedMap(new HashMap<String, RequestFuture>());
  private final CompileServerClasspathManager myClasspathManager = new CompileServerClasspathManager();
  private final Executor myPooledThreadExecutor = new PooledThreadExecutor();
  private final SequentialTaskExecutor myRequestsProcessor = new SequentialTaskExecutor(myPooledThreadExecutor);
  private final Map<String, ProjectData> myProjectDataMap = Collections.synchronizedMap(new HashMap<String, ProjectData>());

  private final BuildManagerPeriodicTask myAutoMakeTask = new BuildManagerPeriodicTask() {
    @Override
    protected int getDelay() {
      return Registry.intValue("compiler.automake.trigger.delay", MAKE_TRIGGER_DELAY);
    }

    @Override
    protected void runTask() {
      runAutoMake();
    }
  };

  private final BuildManagerPeriodicTask myDocumentSaveTask = new BuildManagerPeriodicTask() {
    @Override
    protected int getDelay() {
      return Registry.intValue("compiler.document.save.trigger.delay", DOCUMENT_SAVE_TRIGGER_DELAY);
    }

    private final Semaphore mySemaphore = new Semaphore();
    private final Runnable mySaveDocsRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          FileDocumentManager.getInstance().saveAllDocuments();
        }
        finally {
          mySemaphore.up();
        }
      }
    };

    @Override
    public void runTask() {
      if (shouldSaveDocuments()) {
        mySemaphore.down();
        ApplicationManager.getApplication().invokeLater(mySaveDocsRunnable, ModalityState.NON_MODAL);
        mySemaphore.waitFor();
      }
    }

    private boolean shouldSaveDocuments() {
      for (final Project project : getActiveProjects()) {
        if (canStartAutoMake(project)) {
          return true;
        }
      }
      return false;
    }
  };

  private final ChannelGroup myAllOpenChannels = new DefaultChannelGroup("build-manager");
  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();
  private volatile int myListenPort = -1;
  private volatile CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings myGlobals;
  @Nullable
  private final Charset mySystemCharset;

  public BuildManager(final ProjectManager projectManager) {
    final Application application = ApplicationManager.getApplication();
    IS_UNIT_TEST_MODE = application.isUnitTestMode();
    myProjectManager = projectManager;
    mySystemCharset = CharsetToolkit.getDefaultSystemCharset();
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

    final MessageBusConnection conn = application.getMessageBus().connect();
    conn.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (shouldTriggerMake(events)) {
          scheduleAutoMake();
        }
      }

      private boolean shouldTriggerMake(List<? extends VFileEvent> events) {
        if (PowerSaveMode.isEnabled()) {
          return false;
        }
        List<Project> activeProjects = null;
        for (VFileEvent event : events) {
          final VirtualFile eventFile = event.getFile();
          if (eventFile == null || ProjectCoreUtil.isProjectOrWorkspaceFile(eventFile)) {
            continue;
          }

          if (activeProjects == null) {
            activeProjects = getActiveProjects();
            if (activeProjects.isEmpty()) {
              return false;
            }
          }

          for (Project project : activeProjects) {
            if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(eventFile)) {
              return true;
            }
          }
        }
        return false;
      }

    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        scheduleProjectSave();
      }
    });

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        stopListening();
      }
    });
  }

  private List<Project> getActiveProjects() {
    final Project[] projects = myProjectManager.getOpenProjects();
    if (projects.length == 0) {
      return Collections.emptyList();
    }
    final List<Project> projectList = new SmartList<Project>();
    for (Project project : projects) {
      if (project.isDefault() || project.isDisposed() || !project.isInitialized()) {
        continue;
      }
      projectList.add(project);
    }
    return projectList;
  }

  public static BuildManager getInstance() {
    return ApplicationManager.getApplication().getComponent(BuildManager.class);
  }

  public void notifyFilesChanged(final Collection<File> paths) {
    doNotify(paths, false);
  }

  public void notifyFilesDeleted(Collection<File> paths) {
    doNotify(paths, true);
  }

  public void runCommand(Runnable command) {
    myRequestsProcessor.submit(command);
  }

  private void doNotify(final Collection<File> paths, final boolean notifyDeletion) {
    // ensure events processed in the order they arrived
    runCommand(new Runnable() {

      @Override
      public void run() {
        final List<String> filtered = new ArrayList<String>(paths.size());
        for (File file : paths) {
          final String path = FileUtil.toSystemIndependentName(file.getPath());
          if (PATH_FILTER.fun(path)) {
            filtered.add(path);
          }
        }
        if (filtered.isEmpty()) {
          return;
        }
        synchronized (myProjectDataMap) {
          if (IS_UNIT_TEST_MODE) {
            if (notifyDeletion) {
              LOG.info("Registering deleted paths: " + filtered);
            }
            else {
              LOG.info("Registering changed paths: " + filtered);
            }
          }
          for (Map.Entry<String, ProjectData> entry : myProjectDataMap.entrySet()) {
            final ProjectData data = entry.getValue();
            if (notifyDeletion) {
              data.addDeleted(filtered);
            }
            else {
              data.addChanged(filtered);
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

  public static void forceModelLoading(CompileContext context) {
    context.getCompileScope().putUserData(BuildMain.FORCE_MODEL_LOADING_PARAMETER, Boolean.TRUE.toString());
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
    scheduleAutoMake();
  }

  public boolean rescanRequired(Project project) {
    final String projectPath = getProjectPath(project);
    synchronized (myProjectDataMap) {
      final ProjectData data = myProjectDataMap.get(projectPath);
      return data == null || data.myNeedRescan;
    }
  }

  @Nullable
  public List<String> getFilesChangedSinceLastCompilation(Project project) {
    String projectPath = getProjectPath(project);
    synchronized (myProjectDataMap) {
      ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null && !data.myNeedRescan) {
        return new ArrayList<String>(data.myChanged);
      }
      return null;
    }
  }

  @Nullable
  private static String getProjectPath(final Project project) {
    final String url = project.getPresentableUrl();
    if (url == null) {
      return null;
    }
    return VirtualFileManager.extractPath(url);
  }

  public void scheduleAutoMake() {
    if (!IS_UNIT_TEST_MODE && !PowerSaveMode.isEnabled()) {
      myAutoMakeTask.schedule();
    }
  }

  private void scheduleProjectSave() {
    if (!IS_UNIT_TEST_MODE && !PowerSaveMode.isEnabled()) {
      myDocumentSaveTask.schedule();
    }
  }

  private void runAutoMake() {
    final List<RequestFuture> futures = new ArrayList<RequestFuture>();
    for (final Project project : getActiveProjects()) {
      if (!canStartAutoMake(project)) {
        continue;
      }
      final List<String> emptyList = Collections.emptyList();
      final RequestFuture future = scheduleBuild(
        project, false, true, false, CmdlineProtoUtil.createAllModulesScopes(), emptyList, Collections.<String, String>emptyMap(), new AutoMakeMessageHandler(project)
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

  private static boolean canStartAutoMake(Project project) {
    if (project.isDisposed()) {
      return false;
    }
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    if (!config.useOutOfProcessBuild() || !config.MAKE_PROJECT_ON_SAVE) {
      return false;
    }
    if (!config.allowAutoMakeWhileRunningApplication() && hasRunningProcess(project)) {
      return false;
    }
    return true;
  }

  private static boolean hasRunningProcess(Project project) {
    for (RunContentDescriptor descriptor : ExecutionManager.getInstance(project).getContentManager().getAllDescriptors()) {
      final ProcessHandler handler = descriptor.getProcessHandler();
      if (handler != null && !handler.isProcessTerminated()) { // active process
        return true;
      }
    }
    return false;
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
    final Project project, final boolean isRebuild, final boolean isMake,
    final boolean onlyCheckUpToDate, final List<TargetTypeBuildScope> scopes,
    final Collection<String> paths,
    final Map<String, String> userData, final DefaultMessageHandler handler) {

    final String projectPath = getProjectPath(project);
    final UUID sessionId = UUID.randomUUID();

    // ensure server is listening
    if (myListenPort < 0) {
      try {
        synchronized (this) {
          if (myListenPort < 0) {
            myListenPort = startListening();
          }
        }
      }
      catch (Exception e) {
        handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), null));
        handler.sessionTerminated(sessionId);
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
      runCommand(new Runnable() {
        @Override
        public void run() {
          if (future.isCancelled() || project.isDisposed()) {
            handler.sessionTerminated(sessionId);
            future.setDone();
            return;
          }

          CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals = myGlobals;
          if (globals == null) {
            globals = buildGlobalSettings();
            myGlobals = globals;
          }
          CmdlineRemoteProto.Message.ControllerMessage.FSEvent currentFSChanges;
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
              LOG.info("Scheduling build for " +
                       projectPath +
                       "; CHANGED: " +
                       new HashSet<String>(data.myChanged) +
                       "; DELETED: " +
                       new HashSet<String>(data.myDeleted));
            }
            currentFSChanges = data.getAndResetRescanFlag() ? null : data.createNextEvent();
            projectTaskQueue = data.taskQueue;
          }

          final CmdlineRemoteProto.Message.ControllerMessage params;
          if (isRebuild) {
            params = CmdlineProtoUtil.createRebuildRequest(projectPath, scopes, userData, globals);
          }
          else if (onlyCheckUpToDate) {
            params = CmdlineProtoUtil.createUpToDateCheckRequest(projectPath, scopes, paths, userData, globals, currentFSChanges);
          }
          else {
            params = isMake ?
                     CmdlineProtoUtil.createMakeRequest(projectPath, scopes, userData, globals, currentFSChanges) :
                     CmdlineProtoUtil.createForceCompileRequest(projectPath, scopes, paths, userData, globals, currentFSChanges);
          }

          myMessageDispatcher.registerBuildMessageHandler(sessionId, new BuilderMessageHandlerWrapper(handler) {
            @Override
            public void sessionTerminated(UUID sessionId) {
              try {
                super.sessionTerminated(sessionId);
              }
              finally {
                future.setDone();
              }
            }
          }, params);

          try {
            projectTaskQueue.submit(new Runnable() {
              @Override
              public void run() {
                Throwable execFailure = null;
                try {
                  if (project.isDisposed()) {
                    return;
                  }
                  myBuildsInProgress.put(projectPath, future);
                  final OSProcessHandler processHandler = launchBuildProcess(project, myListenPort, sessionId);
                  final StringBuilder stdErrOutput = new StringBuilder();
                  processHandler.addProcessListener(new ProcessAdapter() {
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
                      handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(msg.toString(), null));
                    }
                  }
                  else {
                    handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure("Disconnected from build process", null));
                  }
                }
                catch (Throwable e) {
                  execFailure = e;
                }
                finally {
                  myBuildsInProgress.remove(projectPath);
                  if (myMessageDispatcher.getAssociatedChannel(sessionId) == null) {
                    // either the connection has never been established (process not started or execution failed), or no messages were sent from the launched process.
                    // in this case the session cannot be unregistered by the message dispatcher
                    final BuilderMessageHandler unregistered = myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
                    if (unregistered != null) {
                      if (execFailure != null) {
                        unregistered.handleFailure(sessionId, CmdlineProtoUtil.createFailure(execFailure.getMessage(), execFailure));
                      }
                      unregistered.sessionTerminated(sessionId);
                    }
                  }
                }
              }
            });
          }
          catch (Throwable e) {
            final BuilderMessageHandler unregistered = myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
            if (unregistered != null) {
              unregistered.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
              unregistered.sessionTerminated(sessionId);
            }
          }
        }
      });

      return future;
    }
    catch (Throwable e) {
      handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
      handler.sessionTerminated(sessionId);
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

    final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.Builder cmdBuilder =
      CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.newBuilder();

    cmdBuilder.setGlobalOptionsPath(PathManager.getOptionsPath());

    if (!data.isEmpty()) {
      for (Map.Entry<String, String> entry : data.entrySet()) {
        final String var = entry.getKey();
        final String value = entry.getValue();
        if (var != null && value != null) {
          cmdBuilder.addPathVariable(CmdlineProtoUtil.createPair(var, value));
        }
      }
    }

    return cmdBuilder.build();
  }

  private OSProcessHandler launchBuildProcess(Project project, final int port, final UUID sessionId) throws ExecutionException {
    final String compilerPath;
    final String vmExecutablePath;
    JavaSdkVersion sdkVersion = null;

    final String forcedCompiledJdkHome = Registry.stringValue(COMPILER_PROCESS_JDK_PROPERTY);

    if (StringUtil.isEmptyOrSpaces(forcedCompiledJdkHome)) {
      // choosing sdk with which the build process should be run
      Sdk projectJdk = null;
      int sdkMinorVersion = 0;

      final Set<Sdk> candidates = new HashSet<Sdk>();
      final Sdk defaultSdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (defaultSdk != null && defaultSdk.getSdkType() instanceof JavaSdk) {
        candidates.add(defaultSdk);
      }

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
      if (projectJdk.equals(internalJdk)) {
        final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
        if (systemCompiler == null) {
          throw new ExecutionException("No system java compiler is provided by the JRE. Make sure tools.jar is present in IntelliJ IDEA classpath.");
        }
        compilerPath = ClasspathBootstrap.getResourcePath(systemCompiler.getClass());
      }
      else {
        compilerPath = ((JavaSdk)projectJdk.getSdkType()).getToolsPath(projectJdk);
        if (compilerPath == null) {
          throw new ExecutionException("Cannot determine path to 'tools.jar' library for " + projectJdk.getName() + " (" + projectJdk.getHomePath() + ")");
        }
      }

      vmExecutablePath = ((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk);
    }
    else {
      compilerPath = new File(forcedCompiledJdkHome, "lib/tools.jar").getAbsolutePath();
      vmExecutablePath = new File(forcedCompiledJdkHome, "bin/java").getAbsolutePath();
    }

    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(vmExecutablePath);
    //cmdLine.addParameter("-XX:MaxPermSize=150m");
    //cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");
    int heapSize = config.COMPILER_PROCESS_HEAP_SIZE;

    // todo: remove when old make implementation is removed
    if (heapSize == CompilerWorkspaceConfiguration.DEFAULT_COMPILE_PROCESS_HEAP_SIZE) {
      // check if javac is set to use larger heap, and if so, use it.
      heapSize = Math.max(heapSize, JavacConfiguration.getOptions(project, JavacConfiguration.class).MAXIMUM_HEAP_SIZE);
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
    cmdLine.addParameter("-D"+ GlobalOptions.COMPILE_PARALLEL_OPTION +"=" + Boolean.toString(config.PARALLEL_COMPILATION));

    boolean isProfilingMode = false;
    final String additionalOptions = config.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS;
    if (!StringUtil.isEmpty(additionalOptions)) {
      final StringTokenizer tokenizer = new StringTokenizer(additionalOptions, " ", false);
      while (tokenizer.hasMoreTokens()) {
        final String option = tokenizer.nextToken();
        if ("-Dprofiling.mode=true".equals(option)) {
          isProfilingMode = true;
        }
        cmdLine.addParameter(option);
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

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    if (mySystemCharset != null) {
      cmdLine.setCharset(mySystemCharset);
      cmdLine.addParameter("-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + mySystemCharset.name());
    }
    cmdLine.addParameter("-D" + JpsGlobalLoader.FILE_TYPES_COMPONENT_NAME_KEY + "=" + FileTypeManagerImpl.getFileTypeComponentName());
    for (String name : new String[]{"user.language", "user.country", "user.region", PathManager.PROPERTY_HOME_PATH}) {
      final String value = System.getProperty(name);
      if (value != null) {
        cmdLine.addParameter("-D" + name + "=" + value);
      }
    }

    final File workDirectory = getBuildSystemDirectory();
    workDirectory.mkdirs();
    ensureLogConfigExists(workDirectory);

    cmdLine.addParameter("-Djava.io.tmpdir=" + FileUtil.toSystemIndependentName(workDirectory.getPath()) + "/_temp_");

    final List<String> cp = ClasspathBootstrap.getBuildProcessApplicationClasspath();
    cp.add(compilerPath);
    cp.addAll(myClasspathManager.getCompileServerPluginsClasspath(project));
    if (isProfilingMode) {
      cp.add(new File(workDirectory, "yjp-controller-api-redist.jar").getPath());
      cmdLine.addParameter("-agentlib:yjpagent=disablej2ee,disablealloc,sessionname=ExternalBuild");
    }

    cmdLine.addParameter("-classpath");
    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter(BuildMain.class.getName());
    cmdLine.addParameter("127.0.0.1");
    cmdLine.addParameter(Integer.toString(port));
    cmdLine.addParameter(sessionId.toString());

    cmdLine.addParameter(FileUtil.toSystemIndependentName(workDirectory.getPath()));

    cmdLine.setWorkDirectory(workDirectory);

    final Process process = cmdLine.createProcess();

    return new OSProcessHandler(process, null, mySystemCharset) {
      @Override
      protected boolean shouldDestroyProcessRecursively() {
        return true;
      }
    };
  }

  public File getBuildSystemDirectory() {
    return new File(mySystemDirectory, SYSTEM_ROOT);
  }

  @Nullable
  public File getProjectSystemDirectory(Project project) {
    final String projectPath = getProjectPath(project);
    return projectPath != null? Utils.getDataStorageRoot(getBuildSystemDirectory(), projectPath) : null;
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
    final ChannelFactory channelFactory = new NioServerSocketChannelFactory(myPooledThreadExecutor, myPooledThreadExecutor, 1);
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
    final Channel serverChannel = bootstrap.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort));
    myAllOpenChannels.add(serverChannel);
    return listenPort;
  }

  @TestOnly
  public void stopWatchingProject(Project project) {
    myProjectDataMap.remove(getProjectPath(project));
  }

  private static String classpathToString(List<String> cp) {
    StringBuilder builder = new StringBuilder();
    for (String file : cp) {
      if (builder.length() > 0) {
        builder.append(File.pathSeparator);
      }
      builder.append(FileUtil.toCanonicalPath(file));
    }
    return builder.toString();
  }

  private static class BuilderMessageHandlerWrapper implements BuilderMessageHandler {
    private final DefaultMessageHandler myHandler;

    public BuilderMessageHandlerWrapper(DefaultMessageHandler handler) {
      myHandler = handler;
    }

    @Override
    public void buildStarted(UUID sessionId) {
      myHandler.buildStarted(sessionId);
    }

    @Override
    public void handleBuildMessage(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage msg) {
      myHandler.handleBuildMessage(channel, sessionId, msg);
    }

    @Override
    public void handleFailure(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
      myHandler.handleFailure(sessionId, failure);
    }

    @Override
    public void sessionTerminated(UUID sessionId) {
      myHandler.sessionTerminated(sessionId);
    }
  }

  private static abstract class BuildManagerPeriodicTask implements Runnable {
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    private final AtomicBoolean myInProgress = new AtomicBoolean(false);
    private final Runnable myTaskRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          runTask();
        }
        finally {
          myInProgress.set(false);
        }
      }
    };

    public final void schedule() {
      myAlarm.cancelAllRequests();
      final int delay = Math.max(100, getDelay());
      myAlarm.addRequest(this, delay);
    }

    protected abstract int getDelay();

    protected abstract void runTask();

    @Override
    public final void run() {
      if (!HeavyProcessLatch.INSTANCE.isRunning() && !myInProgress.getAndSet(true)) {
        try {
          ApplicationManager.getApplication().executeOnPooledThread(myTaskRunnable);
        }
        catch (RejectedExecutionException ignored) {
          // we were shut down
          myInProgress.set(false);
        }
        catch (Throwable e) {
          myInProgress.set(false);
          throw new RuntimeException(e);
        }
      }
      else {
        schedule();
      }
    }
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
      final String projectPath = getProjectPath(project);
      Disposer.register(project, new Disposable() {
        @Override
        public void dispose() {
          myProjectDataMap.remove(projectPath);
        }
      });
      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          scheduleAutoMake(); // run automake after project opened
        }
      });
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
    private final Set<String> myChanged = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    private final Set<String> myDeleted = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
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
  }

}
