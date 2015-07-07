/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.impl.BuildProcessClasspathManager;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionAdapter;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.DataManager;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
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
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import gnu.trove.THashSet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.internal.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.io.ChannelRegistrar;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;

import javax.swing.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class BuildManager implements ApplicationComponent{
  public static final Key<Boolean> ALLOW_AUTOMAKE = Key.create("_allow_automake_when_process_is_active_");
  private static final Key<Integer> COMPILER_PROCESS_DEBUG_PORT = Key.create("_compiler_process_debug_port_");
  private static final Key<String> FORCE_MODEL_LOADING_PARAMETER = Key.create(BuildParametersKeys.FORCE_MODEL_LOADING);
  private static final Key<CharSequence> STDERR_OUTPUT = Key.create("_process_launch_errors_");
  private static final SimpleDateFormat USAGE_STAMP_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildManager");
  private static final String COMPILER_PROCESS_JDK_PROPERTY = "compiler.process.jdk";
  public static final String SYSTEM_ROOT = "compile-server";
  public static final String TEMP_DIR_NAME = "_temp_";
  // do not make static in order not to access application on class load
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

  private final Map<TaskFuture, Project> myAutomakeFutures = Collections.synchronizedMap(new HashMap<TaskFuture, Project>());
  private final Map<String, RequestFuture> myBuildsInProgress = Collections.synchronizedMap(new HashMap<String, RequestFuture>());
  private final Map<String, Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>>> myPreloadedBuilds =
    Collections.synchronizedMap(new HashMap<String, Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>>>());
  private final BuildProcessClasspathManager myClasspathManager = new BuildProcessClasspathManager();
  private final SequentialTaskExecutor myRequestsProcessor = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);
  private final Map<String, ProjectData> myProjectDataMap = Collections.synchronizedMap(new HashMap<String, ProjectData>());

  private final BuildManagerPeriodicTask myAutoMakeTask = new BuildManagerPeriodicTask() {
    @Override
    protected int getDelay() {
      return Registry.intValue("compiler.automake.trigger.delay");
    }

    @Override
    protected void runTask() {
      runAutoMake();
    }
  };

  private final BuildManagerPeriodicTask myDocumentSaveTask = new BuildManagerPeriodicTask() {
    @Override
    protected int getDelay() {
      return Registry.intValue("compiler.document.save.trigger.delay");
    }

    private final Semaphore mySemaphore = new Semaphore();
    private final Runnable mySaveDocsRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).saveAllDocuments(false);
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
      final Project contextProject = getCurrentContextProject();
      return contextProject != null && canStartAutoMake(contextProject);
    }
  };

  private final Runnable myGCTask = new Runnable() {
    // should be executed from the main queue with runCommand!
    @Override
    public void run() {
      // todo: make customizable in UI?
      final int unusedThresholdDays = Registry.intValue("compiler.build.data.unused.threshold", -1);
      if (unusedThresholdDays <= 0) {
        return;
      }
      final File buildSystemDir = getBuildSystemDirectory();
      final File[] dirs = buildSystemDir.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return pathname.isDirectory() && !TEMP_DIR_NAME.equals(pathname.getName());
        }
      });
      if (dirs != null) {
        final Date now = new Date();
        for (File buildDataProjectDir : dirs) {
          final File usageFile = getUsageFile(buildDataProjectDir);
          if (usageFile.exists()) {
            final Pair<Date, File> usageData = readUsageFile(usageFile);
            if (usageData != null) {
              final File projectFile = usageData.second;
              if ((projectFile != null && !projectFile.exists()) || DateFormatUtil.getDifferenceInDays(usageData.first, now) > unusedThresholdDays) {
                LOG.info("Clearing project build data because the project does not exist or was not opened for more than " + unusedThresholdDays + " days: " + buildDataProjectDir.getPath());
                FileUtil.delete(buildDataProjectDir);
              }
            }
          }
          else {
            updateUsageFile(null, buildDataProjectDir); // set usage stamp to start countdown
          }
        }
      }
    }
  };

  private final ChannelRegistrar myChannelRegistrar = new ChannelRegistrar();

  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();
  private volatile int myListenPort = -1;
  @NotNull
  private final Charset mySystemCharset = CharsetToolkit.getDefaultSystemCharset();

  public BuildManager(final ProjectManager projectManager) {
    final Application application = ApplicationManager.getApplication();
    IS_UNIT_TEST_MODE = application.isUnitTestMode();
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

        Project project = null;
        ProjectFileIndex fileIndex = null;

        for (VFileEvent event : events) {
          final VirtualFile eventFile = event.getFile();
          if (eventFile == null) {
            continue;
          }
          if (!eventFile.isValid()) {
            return true; // should be deleted
          }

          if (project == null) {
            // lazy init
            project = getCurrentContextProject();
            if (project == null) {
              return false;
            }
            fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          }

          if (fileIndex.isInContent(eventFile)) {
            if (ProjectCoreUtil.isProjectOrWorkspaceFile(eventFile)) {
              continue;
            }

            return true;
          }
        }
        return false;
      }

    });

    conn.subscribe(BatchFileChangeListener.TOPIC, new BatchFileChangeListener.Adapter() {
      public void batchChangeStarted(Project project) {
        cancelAutoMakeTasks(project);
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

    JobScheduler.getScheduler().scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        runCommand(myGCTask);
      }
    }, 3, 180, TimeUnit.MINUTES);
  }

  private List<Project> getOpenProjects() {
    final Project[] projects = myProjectManager.getOpenProjects();
    if (projects.length == 0) {
      return Collections.emptyList();
    }
    final List<Project> projectList = new SmartList<Project>();
    for (Project project : projects) {
      if (isValidProject(project)) {
        projectList.add(project);
      }
    }
    return projectList;
  }

  private static boolean isValidProject(@Nullable Project project) {
    return project != null && !project.isDisposed() && !project.isDefault() && project.isInitialized();
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
                channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, message));
              }
            }
          }
        }
      }
    });
  }

  public static void forceModelLoading(CompileContext context) {
    context.getCompileScope().putUserData(FORCE_MODEL_LOADING_PARAMETER, Boolean.TRUE.toString());
  }

  public void clearState(Project project) {
    final String projectPath = getProjectPath(project);

    cancelPreloadedBuilds(projectPath);

    synchronized (myProjectDataMap) {
      final ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null) {
        data.dropChanges();
      }
    }
    scheduleAutoMake();
  }

  public boolean isProjectWatched(Project project) {
    return myProjectDataMap.containsKey(getProjectPath(project));
  }

  @Nullable
  public List<String> getFilesChangedSinceLastCompilation(Project project) {
    String projectPath = getProjectPath(project);
    synchronized (myProjectDataMap) {
      ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null && !data.myNeedRescan) {
        return convertToStringPaths(data.myChanged);
      }
      return null;
    }
  }

  private static List<String> convertToStringPaths(final Collection<InternedPath> interned) {
    final ArrayList<String> list = new ArrayList<String>(interned.size());
    for (InternedPath path : interned) {
      list.add(path.getValue());
    }
    return list;
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
    final Project project = getCurrentContextProject();
    if (project == null || !canStartAutoMake(project)) {
      return;
    }
    final List<TargetTypeBuildScope> scopes = CmdlineProtoUtil.createAllModulesScopes(false);
    final AutoMakeMessageHandler handler = new AutoMakeMessageHandler(project);
    final TaskFuture future = scheduleBuild(
      project, false, true, false, scopes, Collections.<String>emptyList(), Collections.<String, String>emptyMap(), handler
    );
    if (future != null) {
      myAutomakeFutures.put(future, project);
      try {
        future.waitFor();
      }
      finally {
        myAutomakeFutures.remove(future);
      }
    }
  }

  private static boolean canStartAutoMake(@NotNull Project project) {
    if (project.isDisposed()) {
      return false;
    }
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    if (!config.MAKE_PROJECT_ON_SAVE) {
      return false;
    }
    if (!config.allowAutoMakeWhileRunningApplication() && hasRunningProcess(project)) {
      return false;
    }
    return true;
  }

  @Nullable
  private Project getCurrentContextProject() {
    return getContextProject(null);
  }

  @Nullable
  private Project getContextProject(@Nullable Window window) {
    final List<Project> openProjects = getOpenProjects();
    if (openProjects.isEmpty()) {
      return null;
    }
    if (openProjects.size() == 1) {
      return openProjects.get(0);
    }

    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (window == null) {
        return null;
      }
    }

    Component comp = window;
    while (true) {
      final Container _parent = comp.getParent();
      if (_parent == null) {
        break;
      }
      comp = _parent;
    }

    Project project = null;
    if (comp instanceof IdeFrame) {
      project = ((IdeFrame)comp).getProject();
    }
    if (project == null) {
      project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(comp));
    }

    return isValidProject(project)? project : null;
  }

  private static boolean hasRunningProcess(Project project) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (!handler.isProcessTerminated() && !ALLOW_AUTOMAKE.get(handler, Boolean.FALSE)) { // active process
        return true;
      }
    }
    return false;
  }

  public Collection<TaskFuture> cancelAutoMakeTasks(Project project) {
    final Collection<TaskFuture> futures = new SmartList<TaskFuture>();
    synchronized (myAutomakeFutures) {
      for (Map.Entry<TaskFuture, Project> entry : myAutomakeFutures.entrySet()) {
        if (entry.getValue().equals(project)) {
          final TaskFuture future = entry.getKey();
          future.cancel(false);
          futures.add(future);
        }
      }
    }
    return futures;
  }

  private void cancelPreloadedBuilds(final String projectPath) {
    runCommand(new Runnable() {
      @Override
      public void run() {
        Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> pair = takePreloadedProcess(projectPath);
        if (pair != null) {
          final RequestFuture<PreloadedProcessMessageHandler> future = pair.first;
          final OSProcessHandler processHandler = pair.second;
          myMessageDispatcher.cancelSession(future.getRequestID());
          // waiting for preloaded process from project's task queue guarantees no build is started for this project
          // until this one gracefully exits and closes all its storages
          getProjectData(projectPath).taskQueue.submit(new Runnable() {
            @Override
            public void run() {
              Throwable error = null;
              try {
                while (!processHandler.waitFor()) {
                  LOG.info("processHandler.waitFor() returned false for session " + future.getRequestID() + ", continue waiting");
                }
              }
              catch (Throwable e) {
                error = e;
              }
              finally {
                notifySessionTerminationIfNeeded(future.getRequestID(), error);
              }
            }
          });
        }
      }
    });
  }

  @Nullable
  private Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> takePreloadedProcess(String projectPath) {
    Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> result;
    final Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>> preloadProgress = myPreloadedBuilds.remove(projectPath);
    try {
      result = preloadProgress != null ? preloadProgress.get() : null;
    }
    catch (Throwable e) {
      LOG.info(e);
      result = null;
    }
    return result != null && !result.first.isDone()? result : null;
  }

  @Nullable
  public TaskFuture scheduleBuild(
    final Project project, final boolean isRebuild, final boolean isMake,
    final boolean onlyCheckUpToDate, final List<TargetTypeBuildScope> scopes,
    final Collection<String> paths,
    final Map<String, String> userData, final DefaultMessageHandler messageHandler) {

    final String projectPath = getProjectPath(project);
    final boolean isAutomake = messageHandler instanceof AutoMakeMessageHandler;
    final BuilderMessageHandler handler = new NotifyingMessageHandler(project, messageHandler, isAutomake);
    try {
      ensureListening();
    }
    catch (Exception e) {
      final UUID sessionId = UUID.randomUUID(); // the actual session did not start, use random UUID
      handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), null));
      handler.sessionTerminated(sessionId);
      return null;
    }

    final DelegateFuture<BuilderMessageHandler> _future = new DelegateFuture<BuilderMessageHandler>();
    // by using the same queue that processes events we ensure that
    // the build will be aware of all events that have happened before this request
    runCommand(new Runnable() {
      @Override
      public void run() {

        final Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> preloaded = takePreloadedProcess(projectPath);
        final RequestFuture<PreloadedProcessMessageHandler> preloadedFuture = preloaded != null? preloaded.first : null;
        final boolean usingPreloadedProcess = preloadedFuture != null;

        final UUID sessionId;
        if (usingPreloadedProcess) {
          LOG.info("Using preloaded build process to compile " + projectPath);
          sessionId = preloadedFuture.getRequestID();
          preloadedFuture.getMessageHandler().setDelegateHandler(handler);
        }
        else {
          sessionId = UUID.randomUUID();
        }

        final RequestFuture<? extends BuilderMessageHandler> future = usingPreloadedProcess? preloadedFuture : new RequestFuture<BuilderMessageHandler>(handler, sessionId, new CancelBuildSessionAction<BuilderMessageHandler>());
        _future.setDelegate(future);

        if (!usingPreloadedProcess && (future.isCancelled() || project.isDisposed())) {
          // in case of preloaded process the process was already running, so the handler will be notified upon process termination
          handler.sessionTerminated(sessionId);
          ((BasicFuture)future).setDone();
          return;
        }

        final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals =
          CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.newBuilder().setGlobalOptionsPath(PathManager.getOptionsPath()).build();
        CmdlineRemoteProto.Message.ControllerMessage.FSEvent currentFSChanges;
        final SequentialTaskExecutor projectTaskQueue;
        final boolean needRescan;
        synchronized (myProjectDataMap) {
          final ProjectData data = getProjectData(projectPath);
          if (isRebuild) {
            data.dropChanges();
          }
          if (IS_UNIT_TEST_MODE) {
            LOG.info("Scheduling build for " +
                     projectPath +
                     "; CHANGED: " +
                     new HashSet<String>(convertToStringPaths(data.myChanged)) +
                     "; DELETED: " +
                     new HashSet<String>(convertToStringPaths(data.myDeleted)));
          }
          needRescan = data.getAndResetRescanFlag();
          currentFSChanges = needRescan ? null : data.createNextEvent();
          projectTaskQueue = data.taskQueue;
        }

        final CmdlineRemoteProto.Message.ControllerMessage params;
        if (isRebuild) {
          params = CmdlineProtoUtil.createBuildRequest(projectPath, scopes, Collections.<String>emptyList(), userData, globals, null);
        }
        else if (onlyCheckUpToDate) {
          params = CmdlineProtoUtil.createUpToDateCheckRequest(projectPath, scopes, paths, userData, globals, currentFSChanges);
        }
        else {
          params = CmdlineProtoUtil.createBuildRequest(projectPath, scopes, isMake ? Collections.<String>emptyList() : paths, userData, globals, currentFSChanges);
        }
        if (!usingPreloadedProcess) {
          myMessageDispatcher.registerBuildMessageHandler(future, params);
        }

        try {
          projectTaskQueue.submit(new Runnable() {
            @Override
            public void run() {
              Throwable execFailure = null;
              try {
                if (project.isDisposed()) {
                  if (usingPreloadedProcess) {
                    future.cancel(false);
                  }
                  else {
                    return;
                  }
                }
                myBuildsInProgress.put(projectPath, future);
                final OSProcessHandler processHandler;
                CharSequence errorsOnLaunch;
                if (usingPreloadedProcess) {
                  final boolean paramsSent = myMessageDispatcher.sendBuildParameters(future.getRequestID(), params);
                  if (!paramsSent) {
                    myMessageDispatcher.cancelSession(future.getRequestID());
                  }
                  processHandler = preloaded.second;
                  errorsOnLaunch = STDERR_OUTPUT.get(processHandler);
                }
                else {
                  if (isAutomake && needRescan) {
                    // if project state was cleared because of roots changed or this is the first compilation after project opening,
                    // ensure project model is saved on disk, so that automake sees the latest model state.
                    // For ordinary make all project, app settings and unsaved docs are always saved before build starts.
                    try {
                      SwingUtilities.invokeAndWait(new Runnable() {
                        public void run() {
                          project.save();
                        }
                      });
                    }
                    catch(Throwable e) {
                      LOG.info(e);
                    }
                  }
                  
                  processHandler = launchBuildProcess(project, myListenPort, sessionId, false);
                  errorsOnLaunch = new StringBuffer();
                  processHandler.addProcessListener(new StdOutputCollector((StringBuffer)errorsOnLaunch));
                  processHandler.startNotify();
                }

                Integer debugPort = processHandler.getUserData(COMPILER_PROCESS_DEBUG_PORT);
                if (debugPort != null) {
                  String message = "Make: waiting for debugger connection on port " + debugPort;
                  messageHandler.handleCompileMessage(sessionId, CmdlineProtoUtil.createCompileProgressMessageResponse(message).getCompileMessage());
                }

                while (!processHandler.waitFor()) {
                  LOG.info("processHandler.waitFor() returned false for session " + sessionId + ", continue waiting");
                }

                final int exitValue = processHandler.getProcess().exitValue();
                if (exitValue != 0) {
                  final StringBuilder msg = new StringBuilder();
                  msg.append("Abnormal build process termination: ");
                  if (errorsOnLaunch != null && errorsOnLaunch.length() > 0) {
                    msg.append("\n").append(errorsOnLaunch);
                  }
                  else {
                    msg.append("unknown error");
                  }
                  handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(msg.toString(), null));
                }

              }
              catch (Throwable e) {
                execFailure = e;
              }
              finally {
                myBuildsInProgress.remove(projectPath);
                notifySessionTerminationIfNeeded(sessionId, execFailure);

                if (isProcessPreloadingEnabled(project)) {
                  runCommand(new Runnable() {
                    public void run() {
                      if (!myPreloadedBuilds.containsKey(projectPath)) {
                        try {
                          final Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>> preloadResult = launchPreloadedBuildProcess(project, projectTaskQueue);
                          myPreloadedBuilds.put(projectPath, preloadResult);
                        }
                        catch (Throwable e) {
                          LOG.info("Error pre-loading build process for project " + projectPath, e);
                        }
                      }
                    }
                  });
                }

              }
            }
          });
        }
        catch (Throwable e) {
          handleProcessExecutionFailure(sessionId, e);
        }
      }
    });

    return _future;
  }

  private boolean isProcessPreloadingEnabled(Project project) {
    // automatically disable process preloading when debugging or testing
    if (IS_UNIT_TEST_MODE || !Registry.is("compiler.process.preload") || Registry.intValue("compiler.process.debug.port") > 0) {
      return false;
    }
    if (project.isDisposed()) {
      return true;
    }
    for (BuildProcessParametersProvider provider : project.getExtensions(BuildProcessParametersProvider.EP_NAME)) {
      if (!provider.isProcessPreloadingEnabled()) {
        return false;
      }
    }
    return true;
  }

  private void notifySessionTerminationIfNeeded(UUID sessionId, @Nullable Throwable execFailure) {
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

  private void handleProcessExecutionFailure(UUID sessionId, Throwable e) {
    final BuilderMessageHandler unregistered = myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
    if (unregistered != null) {
      unregistered.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
      unregistered.sessionTerminated(sessionId);
    }
  }

  @NotNull
  private ProjectData getProjectData(String projectPath) {
    synchronized (myProjectDataMap) {
      ProjectData data = myProjectDataMap.get(projectPath);
      if (data == null) {
        data = new ProjectData(new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE));
        myProjectDataMap.put(projectPath, data);
      }
      return data;
    }
  }

  private void ensureListening() throws Exception {
    if (myListenPort < 0) {
      synchronized (this) {
        if (myListenPort < 0) {
          myListenPort = startListening();
        }
      }
    }
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

  @NotNull
  public static Pair<Sdk, JavaSdkVersion> getBuildProcessRuntimeSdk(Project project) {
    Sdk projectJdk = null;
    int sdkMinorVersion = 0;
    JavaSdkVersion sdkVersion = null;

    final Set<Sdk> candidates = new HashSet<Sdk>();
    final Sdk defaultSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (defaultSdk != null && defaultSdk.getSdkType() instanceof JavaSdkType) {
      candidates.add(defaultSdk);
    }

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
        candidates.add(sdk);
      }
    }

    // now select the latest version from the sdks that are used in the project, but not older than the internal sdk version
    final JavaSdk javaSdkType = JavaSdk.getInstance();
    for (Sdk candidate : candidates) {
      final String vs = candidate.getVersionString();
      if (vs != null) {
        final JavaSdkVersion candidateVersion = javaSdkType.getVersion(vs);
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
      sdkVersion = javaSdkType.getVersion(internalJdk);
    }
    return Pair.create(projectJdk, sdkVersion);
  }

  private Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>> launchPreloadedBuildProcess(final Project project, SequentialTaskExecutor projectTaskQueue) throws Exception {
    ensureListening();

    // launching build process from projectTaskQueue ensures that no other build process for this project is currently running
    return projectTaskQueue.submit(new Callable<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>>() {
      public Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> call() throws Exception {
        if (project.isDisposed()) {
          return null;
        }
        final RequestFuture<PreloadedProcessMessageHandler> future = new RequestFuture<PreloadedProcessMessageHandler>(new PreloadedProcessMessageHandler(), UUID.randomUUID(), new CancelBuildSessionAction<PreloadedProcessMessageHandler>());
        try {
          myMessageDispatcher.registerBuildMessageHandler(future, null);
          final OSProcessHandler processHandler = launchBuildProcess(project, myListenPort, future.getRequestID(), true);
          final StringBuffer errors = new StringBuffer();
          processHandler.addProcessListener(new StdOutputCollector(errors));
          STDERR_OUTPUT.set(processHandler, errors);

          processHandler.startNotify();
          return Pair.create(future, processHandler);
        }
        catch (Throwable e) {
          handleProcessExecutionFailure(future.getRequestID(), e);
          throw e instanceof Exception? (Exception)e : new RuntimeException(e);
        }
      }
    });
  }

  private OSProcessHandler launchBuildProcess(Project project, final int port, final UUID sessionId, boolean requestProjectPreload) throws ExecutionException {
    final String compilerPath;
    final String vmExecutablePath;
    JavaSdkVersion sdkVersion = null;

    final String forcedCompiledJdkHome = Registry.stringValue(COMPILER_PROCESS_JDK_PROPERTY);

    if (StringUtil.isEmptyOrSpaces(forcedCompiledJdkHome)) {
      // choosing sdk with which the build process should be run
      final Pair<Sdk, JavaSdkVersion> pair = getBuildProcessRuntimeSdk(project);
      final Sdk projectJdk = pair.first;
      sdkVersion = pair.second;

      final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      // validate tools.jar presence
      final JavaSdkType projectJdkType = (JavaSdkType)projectJdk.getSdkType();
      if (FileUtil.pathsEqual(projectJdk.getHomePath(), internalJdk.getHomePath())) {
        // important: because internal JDK can be either JDK or JRE,
        // this is the most universal way to obtain tools.jar path in this particular case
        final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
        if (systemCompiler == null) {
          throw new ExecutionException("No system java compiler is provided by the JRE. Make sure tools.jar is present in IntelliJ IDEA classpath.");
        }
        compilerPath = ClasspathBootstrap.getResourcePath(systemCompiler.getClass());
      }
      else {
        compilerPath = projectJdkType.getToolsPath(projectJdk);
        if (compilerPath == null) {
          throw new ExecutionException("Cannot determine path to 'tools.jar' library for " + projectJdk.getName() + " (" + projectJdk.getHomePath() + ")");
        }
      }

      vmExecutablePath = projectJdkType.getVMExecutablePath(projectJdk);
    }
    else {
      compilerPath = new File(forcedCompiledJdkHome, "lib/tools.jar").getAbsolutePath();
      vmExecutablePath = new File(forcedCompiledJdkHome, "bin/java").getAbsolutePath();
    }

    final CompilerConfiguration projectConfig = CompilerConfiguration.getInstance(project);
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(vmExecutablePath);
    //cmdLine.addParameter("-XX:MaxPermSize=150m");
    //cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");

    boolean isProfilingMode = false;
    String userDefinedHeapSize = null;
    final List<String> userAdditionalOptionsList = new SmartList<String>();
    final String userAdditionalVMOptions = config.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS;
    final boolean userLocalOptionsActive = !StringUtil.isEmptyOrSpaces(userAdditionalVMOptions);
    final String additionalOptions = userLocalOptionsActive ? userAdditionalVMOptions : projectConfig.getBuildProcessVMOptions();
    if (!StringUtil.isEmptyOrSpaces(additionalOptions)) {
      final StringTokenizer tokenizer = new StringTokenizer(additionalOptions, " ", false);
      while (tokenizer.hasMoreTokens()) {
        final String option = tokenizer.nextToken();
        if (StringUtil.startsWithIgnoreCase(option, "-Xmx")) {
          if (userLocalOptionsActive) {
            userDefinedHeapSize = option;
          }
        }
        else {
          if ("-Dprofiling.mode=true".equals(option)) {
            isProfilingMode = true;
          }
          userAdditionalOptionsList.add(option);
        }
      }
    }

    if (userDefinedHeapSize != null) {
      cmdLine.addParameter(userDefinedHeapSize);
    }
    else {
      final int heapSize = projectConfig.getBuildProcessHeapSize(JavacConfiguration.getOptions(project, JavacConfiguration.class).MAXIMUM_HEAP_SIZE);
      cmdLine.addParameter("-Xmx" + heapSize + "m");
    }

    if (SystemInfo.isMac && sdkVersion != null && JavaSdkVersion.JDK_1_6.equals(sdkVersion) && Registry.is("compiler.process.32bit.vm.on.mac")) {
      // unfortunately -d32 is supported on jdk 1.6 only
      cmdLine.addParameter("-d32");
    }

    cmdLine.addParameter("-Djava.awt.headless=true");
    if (sdkVersion != null && sdkVersion.ordinal() < JavaSdkVersion.JDK_1_9.ordinal()) {
      //-Djava.endorsed.dirs is not supported in JDK 9+, may result in abnormal process termination
      cmdLine.addParameter("-Djava.endorsed.dirs=\"\""); // turn off all jre customizations for predictable behaviour
    }
    if (IS_UNIT_TEST_MODE) {
      cmdLine.addParameter("-Dtest.mode=true");
    }
    cmdLine.addParameter("-Djdt.compiler.useSingleThread=true"); // always run eclipse compiler in single-threaded mode

    if (requestProjectPreload) {
      cmdLine.addParameter("-Dpreload.project.path=" + FileUtil.toCanonicalPath(getProjectPath(project)));
      cmdLine.addParameter("-Dpreload.config.path=" + FileUtil.toCanonicalPath(PathManager.getOptionsPath()));
    }

    final String shouldGenerateIndex = System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION);
    if (shouldGenerateIndex != null) {
      cmdLine.addParameter("-D"+ GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION +"=" + shouldGenerateIndex);
    }
    cmdLine.addParameter("-D"+ GlobalOptions.COMPILE_PARALLEL_OPTION +"=" + Boolean.toString(config.PARALLEL_COMPILATION));
    cmdLine.addParameter("-D"+ GlobalOptions.REBUILD_ON_DEPENDENCY_CHANGE_OPTION + "=" + Boolean.toString(config.REBUILD_ON_DEPENDENCY_CHANGE));

    if (Boolean.TRUE.equals(Boolean.valueOf(System.getProperty("java.net.preferIPv4Stack", "false")))) {
      cmdLine.addParameter("-Djava.net.preferIPv4Stack=true");
    }

    // this will make netty initialization faster on some systems
    cmdLine.addParameter("-Dio.netty.initialSeedUniquifier=" + ThreadLocalRandom.getInitialSeedUniquifier());

    for (String option : userAdditionalOptionsList) {
      cmdLine.addParameter(option);
    }
    if (isProfilingMode) {
      cmdLine.addParameter("-agentlib:yjpagent=disablealloc,delay=10000,sessionname=ExternalBuild");
    }

    // debugging
    final int debugPort = Registry.intValue("compiler.process.debug.port");
    if (debugPort > 0) {
      cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
      cmdLine.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
    }

    if (!Registry.is("compiler.process.use.memory.temp.cache")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION + "=false");
    }

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    cmdLine.setCharset(mySystemCharset);
    cmdLine.addParameter("-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + mySystemCharset.name());
    cmdLine.addParameter("-D" + JpsGlobalLoader.FILE_TYPES_COMPONENT_NAME_KEY + "=" + FileTypeManagerImpl.getFileTypeComponentName());
    String[] propertiesToPass = {"user.language", "user.country", "user.region", PathManager.PROPERTY_PATHS_SELECTOR, "idea.case.sensitive.fs"};
    for (String name : propertiesToPass) {
      final String value = System.getProperty(name);
      if (value != null) {
        cmdLine.addParameter("-D" + name + "=" + value);
      }
    }
    cmdLine.addParameter("-D" + PathManager.PROPERTY_HOME_PATH + "=" + PathManager.getHomePath());
    cmdLine.addParameter("-D" + PathManager.PROPERTY_CONFIG_PATH + "=" + PathManager.getConfigPath());
    cmdLine.addParameter("-D" + PathManager.PROPERTY_PLUGINS_PATH + "=" + PathManager.getPluginsPath());

    cmdLine.addParameter("-D" + GlobalOptions.LOG_DIR_OPTION + "=" + FileUtil.toSystemIndependentName(getBuildLogDirectory().getAbsolutePath()));

    final File workDirectory = getBuildSystemDirectory();
    //noinspection ResultOfMethodCallIgnored
    workDirectory.mkdirs();
    cmdLine.addParameter("-Djava.io.tmpdir=" + FileUtil.toSystemIndependentName(workDirectory.getPath()) + "/" + TEMP_DIR_NAME);

    for (BuildProcessParametersProvider provider : project.getExtensions(BuildProcessParametersProvider.EP_NAME)) {
      final List<String> args = provider.getVMArguments();
      cmdLine.addParameters(args);
    }

    @SuppressWarnings("UnnecessaryFullyQualifiedName")
    final Class<?> launcherClass = org.jetbrains.jps.cmdline.Launcher.class;

    final List<String> launcherCp = new ArrayList<String>();
    launcherCp.add(ClasspathBootstrap.getResourcePath(launcherClass));
    launcherCp.add(compilerPath);
    ClasspathBootstrap.appendJavaCompilerClasspath(launcherCp);
    launcherCp.addAll(BuildProcessClasspathManager.getLauncherClasspath(project));
    cmdLine.addParameter("-classpath");
    cmdLine.addParameter(classpathToString(launcherCp));

    cmdLine.addParameter(launcherClass.getName());

    final List<String> cp = ClasspathBootstrap.getBuildProcessApplicationClasspath(true);
    cp.addAll(myClasspathManager.getBuildProcessPluginsClasspath(project));
    if (isProfilingMode) {
      cp.add(new File(workDirectory, "yjp-controller-api-redist.jar").getPath());
    }
    cmdLine.addParameter(classpathToString(cp));

    cmdLine.addParameter(BuildMain.class.getName());
    cmdLine.addParameter(Boolean.valueOf(System.getProperty("java.net.preferIPv6Addresses", "false"))? "::1" : "127.0.0.1");
    cmdLine.addParameter(Integer.toString(port));
    cmdLine.addParameter(sessionId.toString());

    cmdLine.addParameter(FileUtil.toSystemIndependentName(workDirectory.getPath()));

    cmdLine.setWorkDirectory(workDirectory);

    try {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).beforeBuildProcessStarted(project, sessionId);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    
    final Process process = cmdLine.createProcess();

    final OSProcessHandler processHandler = new OSProcessHandler(process, null, mySystemCharset) {
      @Override
      protected boolean shouldDestroyProcessRecursively() {
        return true;
      }

      @Override
      protected boolean useNonBlockingRead() {
        return false;
      }
    };
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        // re-translate builder's output to idea.log
        final String text = event.getText();
        if (!StringUtil.isEmptyOrSpaces(text)) {
          LOG.info("BUILDER_PROCESS [" + outputType.toString() + "]: " + text.trim());
        }
      }
    });
    if (debugPort > 0) {
      processHandler.putUserData(COMPILER_PROCESS_DEBUG_PORT, debugPort);
    }

    return processHandler;
  }

  public File getBuildSystemDirectory() {
    return new File(mySystemDirectory, SYSTEM_ROOT);
  }

  public File getBuildLogDirectory() {
    return new File(PathManager.getLogPath(), "build-log");
  }

  @Nullable
  public File getProjectSystemDirectory(Project project) {
    final String projectPath = getProjectPath(project);
    return projectPath != null? Utils.getDataStorageRoot(getBuildSystemDirectory(), projectPath) : null;
  }

  private static File getUsageFile(@NotNull File projectSystemDir) {
    return new File(projectSystemDir, "ustamp");
  }

  private static void updateUsageFile(@Nullable Project project, @NotNull File projectSystemDir) {
    final File usageFile = getUsageFile(projectSystemDir);
    StringBuilder content = new StringBuilder();
    try {
      synchronized (USAGE_STAMP_DATE_FORMAT) {
        content.append(USAGE_STAMP_DATE_FORMAT.format(System.currentTimeMillis()));
      }
      if (project != null && !project.isDisposed()) {
        final String projectFilePath = project.getProjectFilePath();
        if (!StringUtil.isEmptyOrSpaces(projectFilePath)) {
          content.append("\n").append(FileUtil.toCanonicalPath(projectFilePath));
        }
      }
      FileUtil.writeToFile(usageFile, content.toString());
    }
    catch (Throwable e) {
      LOG.info(e);
    }
  }

  @Nullable
  private static Pair<Date, File> readUsageFile(File usageFile) {
    try {
      final List<String> lines = FileUtil.loadLines(usageFile, CharsetToolkit.UTF8_CHARSET.name());
      if (!lines.isEmpty()) {
        final String dateString = lines.get(0);
        final Date date;
        synchronized (USAGE_STAMP_DATE_FORMAT) {
          date = USAGE_STAMP_DATE_FORMAT.parse(dateString);
        }
        final File projectFile = lines.size() > 1? new File(lines.get(1)) : null;
        return Pair.create(date, projectFile);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return null;
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

  public void stopListening() {
    myChannelRegistrar.close();
  }

  private int startListening() throws Exception {
    final ServerBootstrap bootstrap = NettyUtil.nioServerBootstrap(new NioEventLoopGroup(1, PooledThreadExecutor.INSTANCE));
    bootstrap.childHandler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(myChannelRegistrar,
                                   new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(CmdlineRemoteProto.Message.getDefaultInstance()),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   myMessageDispatcher);
      }
    });
    Channel serverChannel = bootstrap.bind(NetUtils.getLoopbackAddress(), 0).syncUninterruptibly().channel();
    myChannelRegistrar.add(serverChannel);
    return ((InetSocketAddress)serverChannel.localAddress()).getPort();
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

  private static class NotifyingMessageHandler extends DelegatingMessageHandler {
    private final Project myProject;
    private final BuilderMessageHandler myDelegateHandler;
    private boolean myIsAutomake;

    public NotifyingMessageHandler(@NotNull Project project, @NotNull BuilderMessageHandler delegateHandler, final boolean isAutomake) {
      myProject = project;
      myDelegateHandler = delegateHandler;
      myIsAutomake = isAutomake;
    }

    @Override
    protected BuilderMessageHandler getDelegateHandler() {
      return myDelegateHandler;
    }

    @Override
    public void buildStarted(UUID sessionId) {
      super.buildStarted(sessionId);
      try {
        ApplicationManager
          .getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).buildStarted(myProject, sessionId, myIsAutomake);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    @Override
    public void sessionTerminated(UUID sessionId) {
      try {
        super.sessionTerminated(sessionId);
      }
      finally {
        try {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).buildFinished(myProject, sessionId, myIsAutomake);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private static final class StdOutputCollector extends ProcessAdapter {
    private final Appendable myOutput;
    private int myStoredLength = 0;
    public StdOutputCollector(Appendable outputSink) {
      myOutput = outputSink;
    }

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      String text;

      synchronized (this) {
        if (myStoredLength > 2048) {
          return;
        }
        text = event.getText();
        if (StringUtil.isEmptyOrSpaces(text)) {
          return;
        }
        myStoredLength += text.length();
      }

      try {
        myOutput.append(text);
      }
      catch (IOException ignored) {
      }
    }
  }

  private class ProjectWatcher extends ProjectManagerAdapter {
    private final Map<Project, MessageBusConnection> myConnections = new HashMap<Project, MessageBusConnection>();

    @Override
    public void projectOpened(final Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
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
      conn.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
        private final Set<String> myRootsToRefresh = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
        @Override
        public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
          final String[] roots;
          synchronized (myRootsToRefresh) {
            roots = ArrayUtil.toStringArray(myRootsToRefresh);
            myRootsToRefresh.clear();
          }
          if (roots.length != 0) {
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
              @Override
              public void run() {
                if (project.isDisposed()) {
                  return;
                }
                final List<File> rootFiles = new ArrayList<File>(roots.length);
                for (String root : roots) {
                  rootFiles.add(new File(root));
                }
                // this will ensure that we'll be able to obtain VirtualFile for existing roots
                CompilerUtil.refreshOutputDirectories(rootFiles, false);

                final LocalFileSystem lfs = LocalFileSystem.getInstance();
                final Set<VirtualFile> filesToRefresh = new HashSet<VirtualFile>();
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    if (project.isDisposed()) {
                      return;
                    }
                    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                    for (File root : rootFiles) {
                      final VirtualFile rootFile = lfs.findFileByIoFile(root);
                      if (rootFile != null && fileIndex.isInSourceContent(rootFile)) {
                        filesToRefresh.add(rootFile);
                      }
                    }
                    if (!filesToRefresh.isEmpty()) {
                      lfs.refreshFiles(filesToRefresh, true, true, null);
                    }
                  }
                });
              }
            });
          }
        }

        @Override
        public void fileGenerated(String outputRoot, String relativePath) {
          synchronized (myRootsToRefresh) {
            myRootsToRefresh.add(outputRoot);
          }
        }
      });
      final String projectPath = getProjectPath(project);
      Disposer.register(project, new Disposable() {
        @Override
        public void dispose() {
          cancelPreloadedBuilds(projectPath);
          myProjectDataMap.remove(projectPath);
        }
      });
      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          runCommand(new Runnable() {
            @Override
            public void run() {
              final File projectSystemDir = getProjectSystemDirectory(project);
              if (projectSystemDir != null) {
                updateUsageFile(project, projectSystemDir);
              }
            }
          });
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
      cancelPreloadedBuilds(getProjectPath(project));
      for (TaskFuture future : cancelAutoMakeTasks(project)) {
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
    @NotNull
    final SequentialTaskExecutor taskQueue;
    private final Set<InternedPath> myChanged = new THashSet<InternedPath>();
    private final Set<InternedPath> myDeleted = new THashSet<InternedPath>();
    private long myNextEventOrdinal = 0L;
    private boolean myNeedRescan = true;

    private ProjectData(@NotNull SequentialTaskExecutor taskQueue) {
      this.taskQueue = taskQueue;
    }

    public void addChanged(Collection<String> paths) {
      if (!myNeedRescan) {
        for (String path : paths) {
          final InternedPath _path = InternedPath.create(path);
          myDeleted.remove(_path);
          myChanged.add(_path);
        }
      }
    }

    public void addDeleted(Collection<String> paths) {
      if (!myNeedRescan) {
        for (String path : paths) {
          final InternedPath _path = InternedPath.create(path);
          myChanged.remove(_path);
          myDeleted.add(_path);
        }
      }
    }

    public CmdlineRemoteProto.Message.ControllerMessage.FSEvent createNextEvent() {
      final CmdlineRemoteProto.Message.ControllerMessage.FSEvent.Builder builder =
        CmdlineRemoteProto.Message.ControllerMessage.FSEvent.newBuilder();
      builder.setOrdinal(++myNextEventOrdinal);

      for (InternedPath path : myChanged) {
        builder.addChangedPaths(path.getValue());
      }
      myChanged.clear();

      for (InternedPath path : myDeleted) {
        builder.addDeletedPaths(path.getValue());
      }
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

  private static abstract class InternedPath {
    protected final int[] myPath;

    /**
     * @param path assuming system-independent path with forward slashes
     */
    protected InternedPath(String path) {
      final IntArrayList list = new IntArrayList();
      final StringTokenizer tokenizer = new StringTokenizer(path, "/", false);
      while(tokenizer.hasMoreTokens()) {
        final String element = tokenizer.nextToken();
        list.add(FileNameCache.storeName(element));
      }
      myPath = list.toArray();
    }

    public abstract String getValue();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InternedPath path = (InternedPath)o;

      if (!Arrays.equals(myPath, path.myPath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myPath);
    }

    public static InternedPath create(String path) {
      return path.startsWith("/")? new XInternedPath(path) : new WinInternedPath(path);
    }
  }

  private static class WinInternedPath extends InternedPath {
    private WinInternedPath(String path) {
      super(path);
    }

    @Override
    public String getValue() {
      if (myPath.length == 1) {
        final String name = FileNameCache.getVFileName(myPath[0]).toString();
        // handle case of windows drive letter
        return name.length() == 2 && name.endsWith(":")? name + "/" : name;
      }

      final StringBuilder buf = new StringBuilder();
      for (int element : myPath) {
        if (buf.length() > 0) {
          buf.append("/");
        }
        buf.append(FileNameCache.getVFileName(element));
      }
      return buf.toString();
    }
  }

  private static class XInternedPath extends InternedPath {
    private XInternedPath(String path) {
      super(path);
    }

    @Override
    public String getValue() {
      if (myPath.length > 0) {
        final StringBuilder buf = new StringBuilder();
        for (int element : myPath) {
          buf.append("/").append(FileNameCache.getVFileName(element));
        }
        return buf.toString();
      }
      return "/";
    }
  }

  private static final class DelegateFuture<T> implements TaskFuture<T> {
    @Nullable
    private TaskFuture<? extends T> myDelegate;
    private Boolean myRequestedCancelState = null;

    @NotNull
    public synchronized TaskFuture<? extends T> getDelegate() {
      TaskFuture<? extends T> delegate = myDelegate;
      while (delegate == null) {
        try {
          wait();
        }
        catch (InterruptedException ignored) {
        }
        delegate = myDelegate;
      }
      return delegate;
    }

    public synchronized boolean setDelegate(@NotNull TaskFuture<? extends T> delegate) {
      if (myDelegate == null) {
        try {
          myDelegate = delegate;
          if (myRequestedCancelState != null) {
            myDelegate.cancel(myRequestedCancelState);
          }
        }
        finally {
          notifyAll();
        }
        return true;
      }
      return false;
    }

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      final TaskFuture<? extends T> delegate = myDelegate;
      if (delegate == null) {
        myRequestedCancelState = mayInterruptIfRunning;
        return true;
      }
      return delegate.cancel(mayInterruptIfRunning);
    }

    public void waitFor() {
      getDelegate().waitFor();
    }

    public boolean waitFor(long timeout, TimeUnit unit) {
      return getDelegate().waitFor(timeout, unit);
    }

    public boolean isCancelled() {
      final TaskFuture<? extends T> delegate;
      synchronized (this) {
        delegate = myDelegate;
        if (delegate == null) {
          return myRequestedCancelState != null;
        }
      }
      return delegate.isCancelled();
    }

    public boolean isDone() {
      final TaskFuture<? extends T> delegate;
      synchronized (this) {
        delegate = myDelegate;
        if (delegate == null) {
          return false;
        }
      }
      return delegate.isDone();
    }

    public T get() throws InterruptedException, java.util.concurrent.ExecutionException {
      return getDelegate().get();
    }

    public T get(long timeout, TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
      return getDelegate().get(timeout, unit);
    }
  }

  private class CancelBuildSessionAction<T extends BuilderMessageHandler> implements RequestFuture.CancelAction<T> {
    @Override
    public void cancel(RequestFuture<T> future) throws Exception {
      myMessageDispatcher.cancelSession(future.getRequestID());
    }
  }
}
