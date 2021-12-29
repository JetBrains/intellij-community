// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server;

import com.intellij.DynamicBundle;
import com.intellij.ProjectTopics;
import com.intellij.application.options.RegistryManager;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.YourKitProfilerService;
import com.intellij.compiler.cache.CompilerCacheConfigurator;
import com.intellij.compiler.cache.CompilerCacheStartupActivity;
import com.intellij.compiler.cache.git.GitRepositoryUtil;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompilerConfiguration;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.impl.BuildProcessClasspathManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.execution.wsl.WslPath;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.*;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.internal.ThreadLocalRandom;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.BuiltInServerManagerImpl;
import org.jetbrains.io.ChannelRegistrar;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jvnet.winp.Priority;
import org.jvnet.winp.WinProcess;

import javax.tools.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

public final class BuildManager implements Disposable {
  public static final Key<Boolean> ALLOW_AUTOMAKE = Key.create("_allow_automake_when_process_is_active_");
  private static final Key<Integer> COMPILER_PROCESS_DEBUG_PORT = Key.create("_compiler_process_debug_port_");
  private static final Key<String> FORCE_MODEL_LOADING_PARAMETER = Key.create(BuildParametersKeys.FORCE_MODEL_LOADING);
  private static final Key<CharSequence> STDERR_OUTPUT = Key.create("_process_launch_errors_");
  private static final SimpleDateFormat USAGE_STAMP_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

  public static final String LOW_PRIORITY_REGISTRY_KEY = "compiler.process.low.priority";
  private static final Logger LOG = Logger.getInstance(BuildManager.class);
  private static final String COMPILER_PROCESS_JDK_PROPERTY = "compiler.process.jdk";
  public static final String SYSTEM_ROOT = "compile-server";
  public static final String TEMP_DIR_NAME = "_temp_";
  private static final String[] INHERITED_IDE_VM_OPTIONS = {
    "user.language", "user.country", "user.region", PathManager.PROPERTY_PATHS_SELECTOR, "idea.case.sensitive.fs", "java.net.preferIPv4Stack"
  };

  // do not make static in order not to access application on class load
  private final boolean IS_UNIT_TEST_MODE;
  private static final String IWS_EXTENSION = ".iws";
  private static final String IPR_EXTENSION = ".ipr";
  private static final String IDEA_PROJECT_DIR_PATTERN = "/.idea/";
  private static final Function<String, Boolean> PATH_FILTER =
    SystemInfoRt.isFileSystemCaseSensitive ?
    s -> !(s.contains(IDEA_PROJECT_DIR_PATTERN) || s.endsWith(IWS_EXTENSION) || s.endsWith(IPR_EXTENSION)) :
    s -> !(Strings.endsWithIgnoreCase(s, IWS_EXTENSION) || Strings.endsWithIgnoreCase(s, IPR_EXTENSION) || StringUtil.containsIgnoreCase(s, IDEA_PROJECT_DIR_PATTERN));

  private final String myFallbackSdkHome;
  private final String myFallbackSdkVersion;

  private final Map<TaskFuture<?>, Project> myAutomakeFutures = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, RequestFuture<?>> myBuildsInProgress = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>>> myPreloadedBuilds = Collections.synchronizedMap(new HashMap<>());
  private final BuildProcessClasspathManager myClasspathManager = new BuildProcessClasspathManager(this);
  private final Executor myRequestsProcessor = AppExecutorUtil.createBoundedApplicationPoolExecutor("BuildManager RequestProcessor Pool", 1);
  private final List<VFileEvent> myUnprocessedEvents = new ArrayList<>();
  private final Executor myAutomakeTrigger = AppExecutorUtil.createBoundedApplicationPoolExecutor("BuildManager Auto-Make Trigger", 1);
  private final Map<String, ProjectData> myProjectDataMap = Collections.synchronizedMap(new HashMap<>());
  private final AtomicInteger mySuspendBackgroundTasksCounter = new AtomicInteger(0);

  private final BuildManagerPeriodicTask myAutoMakeTask = new BuildManagerPeriodicTask() {
    @Override
    protected int getDelay() {
      return Registry.intValue("compiler.automake.trigger.delay");
    }

    @Override
    protected void runTask() {
      runAutoMake();
    }

    @Override
    protected boolean shouldPostpone() {
      // Heuristics for automake postpone decision:
      // 1. There are unsaved documents OR
      // 2. The IDE is not idle: the last activity happened less than 3 seconds ago (registry-configurable)
      if (FileDocumentManager.getInstance().getUnsavedDocuments().length > 0) {
        return true;
      }
      final long threshold = Registry.intValue("compiler.automake.postpone.when.idle.less.than", 3000); // todo: UI option instead of registry?
      final long idleSinceLastActivity = ApplicationManager.getApplication().getIdleTime();
      return idleSinceLastActivity < threshold;
    }
  };

  private final BuildManagerPeriodicTask myDocumentSaveTask = new BuildManagerPeriodicTask() {
    @Override
    protected int getDelay() {
      return Registry.intValue("compiler.document.save.trigger.delay");
    }

    @Override
    public void runTask() {
      if (shouldSaveDocuments()) {
        ApplicationManager.getApplication().invokeAndWait(
          () -> ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).saveAllDocuments(false)
        );
      }
    }

    private boolean shouldSaveDocuments() {
      final Project contextProject = getCurrentContextProject();
      return contextProject != null && canStartAutoMake(contextProject);
    }
  };

  private final Runnable myGCTask = () -> {
    // todo: make customizable in UI?
    final int unusedThresholdDays = Registry.intValue("compiler.build.data.unused.threshold", -1);
    if (unusedThresholdDays <= 0) {
      return;
    }

    Collection<File> systemDirs = Collections.singleton(getBuildSystemDirectory().toFile());
    if (Boolean.valueOf(System.getProperty("compiler.build.data.clean.unused.wsl"))) {
      final List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
      if (!distributions.isEmpty()) {
        systemDirs = new ArrayList<>(systemDirs);
        for (WSLDistribution distribution : distributions) {
          final Path wslSystemDir = WslBuildCommandLineBuilder.getWslBuildSystemDirectory(distribution);
          if (wslSystemDir != null) {
            systemDirs.add(wslSystemDir.toFile());
          }
        }
      }
    }

    for (File buildSystemDir : systemDirs) {
      File[] dirs = buildSystemDir.listFiles(pathname -> pathname.isDirectory() && !TEMP_DIR_NAME.equals(pathname.getName()));
      if (dirs != null) {
        final Date now = new Date();
        for (File buildDataProjectDir : dirs) {
          File usageFile = getUsageFile(buildDataProjectDir);
          if (usageFile.exists()) {
            final Pair<Date, File> usageData = readUsageFile(usageFile);
            if (usageData != null) {
              final File projectFile = usageData.second;
              if (projectFile != null && !projectFile.exists() || DateFormatUtil.getDifferenceInDays(usageData.first, now) > unusedThresholdDays) {
                LOG.info("Clearing project build data because the project does not exist or was not opened for more than " + unusedThresholdDays + " days: " + buildDataProjectDir);
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

  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();

  private static class ListeningConnection {
    private final InetAddress myAddress;
    private final ChannelRegistrar myChannelRegistrar = new ChannelRegistrar();
    private volatile int myListenPort = -1;

    private ListeningConnection(InetAddress address) {
      myAddress = address;
    }
  }

  private final List<ListeningConnection> myListeningConnections = new ArrayList<>();

  @NotNull
  private final Charset mySystemCharset = CharsetToolkit.getDefaultSystemCharset();
  private volatile boolean myBuildProcessDebuggingEnabled;

  public BuildManager() {
    final Application application = ApplicationManager.getApplication();
    IS_UNIT_TEST_MODE = application.isUnitTestMode();

    String fallbackSdkHome = System.getProperty(GlobalOptions.FALLBACK_JDK_HOME, null);
    String fallbackSdkVersion = System.getProperty(GlobalOptions.FALLBACK_JDK_VERSION, null);
    if (fallbackSdkHome == null || fallbackSdkVersion == null) {
      // default to the IDE's runtime
      myFallbackSdkHome = getFallbackSdkHome();
      myFallbackSdkVersion = SystemInfo.JAVA_VERSION;
    }
    else {
      myFallbackSdkHome = fallbackSdkHome;
      myFallbackSdkVersion = fallbackSdkVersion;
    }

    MessageBusConnection connection = application.getMessageBus().connect(this);
    connection.subscribe(ProjectManager.TOPIC, new ProjectWatcher());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (!IS_UNIT_TEST_MODE) {
          synchronized (myUnprocessedEvents) {
            myUnprocessedEvents.addAll(events);
          }
          myAutomakeTrigger.execute(() -> {
            if (!application.isDisposed()) {
              ReadAction.run(()-> {
                if (application.isDisposed()) {
                  return;
                }
                final List<VFileEvent> snapshot;
                synchronized (myUnprocessedEvents) {
                  if (myUnprocessedEvents.isEmpty()) {
                    return;
                  }
                  snapshot = new ArrayList<>(myUnprocessedEvents);
                  myUnprocessedEvents.clear();
                }
                if (shouldTriggerMake(snapshot)) {
                  scheduleAutoMake();
                }
              });
            }
            else {
              synchronized (myUnprocessedEvents) {
                myUnprocessedEvents.clear();
              }
            }
          });
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
            if (ProjectUtil.isProjectOrWorkspaceFile(eventFile) || GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(eventFile, project)) {
              // changes in project files or generated stuff should not trigger auto-make
              continue;
            }
            return true;
          }
        }
        return false;
      }

    });

    connection.subscribe(BatchFileChangeListener.TOPIC, new BatchFileChangeListener() {
      @Override
      public void batchChangeStarted(@NotNull Project project, @Nullable String activityName) {
        postponeBackgroundTasks();
        cancelAutoMakeTasks(project);
      }

      @Override
      public void batchChangeCompleted(@NotNull Project project) {
        allowBackgroundTasks(false);
      }
    });

    if (!application.isHeadlessEnvironment()) {
      configureIdleAutomake();
    }

    ShutDownTracker.getInstance().registerShutdownTask(this::stopListening);

    if (!IS_UNIT_TEST_MODE) {
      ScheduledFuture<?> future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> runCommand(myGCTask), 3, 180, TimeUnit.MINUTES);
      Disposer.register(this, () -> future.cancel(false));
    }
  }

  private void configureIdleAutomake() {
    final int idleTimeout = getAutomakeWhileIdleTimeout();
    final int listenerTimeout = idleTimeout > 0 ? idleTimeout : 60000;
    IdeEventQueue.getInstance().addIdleListener(new Runnable() {
      @Override
      public void run() {
        final int currentTimeout = getAutomakeWhileIdleTimeout();
        if (idleTimeout != currentTimeout) {
          // re-schedule with changed period
          IdeEventQueue.getInstance().removeIdleListener(this);
          configureIdleAutomake();
        }
        if (currentTimeout > 0 /*is enabled*/ && !myAutoMakeTask.myInProgress.get()) {
          boolean hasChanges = false;
          synchronized (myProjectDataMap) {
            for (ProjectData data : myProjectDataMap.values()) {
              if (data.hasChanges()) {
                hasChanges = true;
                break;
              }
            }
          }
          if (hasChanges) {
            // only schedule automake if the feature is enabled, no automake is running at the moment and there is at least one watched project with pending changes
            scheduleAutoMake();
          }
        }
      }
    }, listenerTimeout);
  }

  public void postponeBackgroundTasks() {
    mySuspendBackgroundTasksCounter.incrementAndGet();
  }

  public void allowBackgroundTasks(boolean resetState) {
    if (resetState) {
      mySuspendBackgroundTasksCounter.set(0);
    }
    else {
      if (mySuspendBackgroundTasksCounter.decrementAndGet() < 0) {
        mySuspendBackgroundTasksCounter.incrementAndGet();
      }
    }
  }

  private static @NotNull String getFallbackSdkHome() {
    String home = SystemProperties.getJavaHome(); // should point either to jre or jdk
    if (!JdkUtil.checkForJdk(home)) {
      String parent = new File(home).getParent();
      if (parent != null && JdkUtil.checkForJdk(parent)) {
        home = parent;
      }
    }
    return FileUtilRt.toSystemIndependentName(home);
  }

  @NotNull
  private static List<Project> getOpenProjects() {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length == 0) {
      return Collections.emptyList();
    }
    final List<Project> projectList = new SmartList<>();
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
    return ApplicationManager.getApplication().getService(BuildManager.class);
  }

  public void notifyFilesChanged(final Collection<? extends File> paths) {
    doNotify(paths, false);
  }

  public void notifyFilesDeleted(Collection<? extends File> paths) {
    doNotify(paths, true);
  }

  public void runCommand(@NotNull Runnable command) {
    myRequestsProcessor.execute(command);
  }

  private void doNotify(final Collection<? extends File> paths, final boolean notifyDeletion) {
    // ensure events processed in the order they arrived
    runCommand(() -> {
      final List<String> filtered = new ArrayList<>(paths.size());
      for (File file : paths) {
        final String path = FileUtil.toSystemIndependentName(file.getPath());
        if (PATH_FILTER.apply(path)) {
          filtered.add(path);
        }
      }
      if (filtered.isEmpty()) {
        return;
      }
      synchronized (myProjectDataMap) {
        //if (IS_UNIT_TEST_MODE) {
        //  if (notifyDeletion) {
        //    LOG.info("Registering deleted paths: " + filtered);
        //  }
        //  else {
        //    LOG.info("Registering changed paths: " + filtered);
        //  }
        //}
        for (Map.Entry<String, ProjectData> entry : myProjectDataMap.entrySet()) {
          final ProjectData data = entry.getValue();
          if (notifyDeletion) {
            data.addDeleted(filtered);
          }
          else {
            data.addChanged(filtered);
          }
          RequestFuture<?> future = myBuildsInProgress.get(entry.getKey());
          if (future != null && !future.isCancelled() && !future.isDone()) {
            final UUID sessionId = future.getRequestID();
            final Channel channel = myMessageDispatcher.getConnectedChannel(sessionId);
            if (channel != null) {
              CmdlineRemoteProto.Message.ControllerMessage.FSEvent event = data.createNextEvent(wslPathMapper(entry.getKey()));
              final CmdlineRemoteProto.Message.ControllerMessage message = CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(
                CmdlineRemoteProto.Message.ControllerMessage.Type.FS_EVENT
              ).setFsEvent(event).build();
              if (LOG.isDebugEnabled()) {
                LOG.debug("Sending to running build, ordinal=" + event.getOrdinal());
              }
              channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, message));
            }
          }
        }
      }
    });
  }

  @Nullable
  private static Project findProjectByProjectPath(@NotNull String projectPath) {
    return ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), (project) -> projectPath.equals(getProjectPath(project)));
  }

  @NotNull
  private static Function<String, String> wslPathMapper(@Nullable WSLDistribution distr) {
    return distr == null?
      Function.identity() :
      // interned paths collapse repeated slashes so \\wsl$ ends up /wsl$
      path -> path.startsWith("/wsl$")? distr.getWslPath("/" + path) : path;
  }

  private static Function<String, String> wslPathMapper(@NotNull String projectPath) {
    return new Function<>() {
      private Function<String, String> myImpl;

      @Override
      public String apply(String path) {
        if (myImpl != null) {
          return myImpl.apply(path);
        }
        if (path.startsWith("/wsl$")) {
          Project project = findProjectByProjectPath(projectPath);
          myImpl = wslPathMapper(project != null ? findWSLDistributionForBuild(project) : null);
          return myImpl.apply(path);
        }
        return path;
      }
    };
  }

  public static void forceModelLoading(CompileContext context) {
    context.getCompileScope().putUserData(FORCE_MODEL_LOADING_PARAMETER, Boolean.TRUE.toString());
  }

  public void clearState(@NotNull Project project) {
    String projectPath = getProjectPath(project);
    cancelPreloadedBuilds(projectPath);

    boolean clearNameCache = false;
    synchronized (myProjectDataMap) {
      ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null) {
        data.dropChanges();
        clearNameCache = myProjectDataMap.size() == 1;
      }
    }
    if (clearNameCache) {
      InternedPath.clearCache();
    }
    scheduleAutoMake();
  }

  public void clearState() {
    final boolean cleared;
    synchronized (myProjectDataMap) {
      cleared = !myProjectDataMap.isEmpty();
      for (Map.Entry<String, ProjectData> entry : myProjectDataMap.entrySet()) {
        cancelPreloadedBuilds(entry.getKey());
        entry.getValue().dropChanges();
      }
    }
    if (cleared) {
      InternedPath.clearCache();
      scheduleAutoMake();
    }
  }

  public boolean isProjectWatched(@NotNull Project project) {
    return myProjectDataMap.containsKey(getProjectPath(project));
  }

  public @Nullable List<String> getFilesChangedSinceLastCompilation(@NotNull Project project) {
    String projectPath = getProjectPath(project);
    synchronized (myProjectDataMap) {
      ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null && !data.myNeedRescan) {
        return convertToStringPaths(data.myChanged);
      }
      return null;
    }
  }

  private static @NotNull List<String> convertToStringPaths(@NotNull Collection<? extends InternedPath> interned) {
    List<String> list = new ArrayList<>(interned.size());
    for (InternedPath path : interned) {
      list.add(path.getValue());
    }
    return list;
  }

  private static @NotNull String getProjectPath(@NotNull Project project) {
    assert !project.isDefault() : BuildManager.class.getName() + " methods should not be called for default project";
    return VirtualFileManager.extractPath(Objects.requireNonNull(project.getPresentableUrl()));
  }

  public void scheduleAutoMake() {
    if (!IS_UNIT_TEST_MODE && !PowerSaveMode.isEnabled()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Automake scheduled:\n" + getThreadTrace(Thread.currentThread(), 10));
      }
      myAutoMakeTask.schedule();
    }
  }

  @NotNull
  private static String getThreadTrace(Thread thread, final int depth) { // debugging
    final StringBuilder buf = new StringBuilder();
    final StackTraceElement[] trace = thread.getStackTrace();
    for (int i = 0; i < depth && i < trace.length; i++) {
      final StackTraceElement element = trace[i];
      buf.append("\tat ").append(element).append("\n");
    }
    return buf.toString();
  }

  private void scheduleProjectSave() {
    if (!IS_UNIT_TEST_MODE && !PowerSaveMode.isEnabled()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Automake canceled; reason: project save scheduled");
      }
      myAutoMakeTask.cancelPendingExecution();
      myDocumentSaveTask.schedule();
    }
  }

  private void runAutoMake() {

    Collection<Project> projects = Collections.emptyList();
    final int appIdleTimeout = getAutomakeWhileIdleTimeout();
    if (appIdleTimeout > 0 && ApplicationManager.getApplication().getIdleTime() > appIdleTimeout) {
      // been idle quite a time, so try to process all open projects while idle
      projects = getOpenProjects();
    }
    else {
      final Project project = getCurrentContextProject();
      if (project != null) {
        projects = Collections.singleton(project);
      }
    }
    if (projects.isEmpty()) {
      return;
    }
    final List<Pair<TaskFuture<?>, AutoMakeMessageHandler>> futures = new SmartList<>();
    for (Project project : projects) {
      if (!canStartAutoMake(project)) {
        continue;
      }
      final List<TargetTypeBuildScope> scopes = CmdlineProtoUtil.createAllModulesScopes(false);
      final AutoMakeMessageHandler handler = new AutoMakeMessageHandler(project);
      final TaskFuture<?> future = scheduleBuild(
        project, false, true, false, scopes, Collections.emptyList(), Collections.singletonMap(BuildParametersKeys.IS_AUTOMAKE, "true"), handler
      );
      if (future != null) {
        myAutomakeFutures.put(future, project);
        futures.add(Pair.create(future, handler));
      }
    }
    boolean needAdditionalBuild = false;
    for (Pair<TaskFuture<?>, AutoMakeMessageHandler> pair : futures) {
      try {
        pair.first.waitFor();
      }
      catch (Throwable ignored) {
      }
      finally {
        myAutomakeFutures.remove(pair.first);
        if (pair.second.unprocessedFSChangesDetected()) {
          needAdditionalBuild = true;
        }
      }
    }
    if (needAdditionalBuild) {
      scheduleAutoMake();
    }
  }

  private static int getAutomakeWhileIdleTimeout() {
    return RegistryManager.getInstance().intValue("compiler.automake.build.while.idle.timeout", 60000);
  }

  private static boolean canStartAutoMake(@NotNull Project project) {
    if (project.isDisposed()) {
      return false;
    }
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    if (!config.MAKE_PROJECT_ON_SAVE || !TrustedProjects.isTrusted(project)) {
      return false;
    }
    return config.allowAutoMakeWhileRunningApplication() || !hasRunningProcess(project);
  }

  @Nullable
  private static Project getCurrentContextProject() {
    final List<Project> openProjects = getOpenProjects();
    if (openProjects.isEmpty()) {
      return null;
    }
    if (openProjects.size() == 1) {
      return openProjects.get(0);
    }

    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (window == null) {
      window = ComponentUtil.getActiveWindow();
    }

    final Component comp = ComponentUtil.findUltimateParent(window);
    Project project = null;
    if (comp instanceof IdeFrame) {
      project = ((IdeFrame)comp).getProject();
    }

    return isValidProject(project)? project : null;
  }

  private static boolean hasRunningProcess(@NotNull Project project) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (!handler.isProcessTerminated() && !ALLOW_AUTOMAKE.get(handler, Boolean.FALSE)) { // active process
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Collection<TaskFuture<?>> cancelAutoMakeTasks(@NotNull Project project) {
    final Collection<TaskFuture<?>> futures = new SmartList<>();
    synchronized (myAutomakeFutures) {
      for (Map.Entry<TaskFuture<?>, Project> entry : myAutomakeFutures.entrySet()) {
        if (entry.getValue().equals(project)) {
          TaskFuture<?> future = entry.getKey();
          future.cancel(false);
          futures.add(future);
        }
      }
    }
    if (LOG.isDebugEnabled() && !futures.isEmpty()) {
      LOG.debug("Automake cancel (all tasks):\n" + getThreadTrace(Thread.currentThread(), 10));
    }
    return futures;
  }

  private void cancelAllPreloadedBuilds() {
    String[] paths = ArrayUtilRt.toStringArray(myPreloadedBuilds.keySet());
    for (String path : paths) {
      cancelPreloadedBuilds(path);
    }
  }

  private void cancelPreloadedBuilds(@NotNull String projectPath) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cancel preloaded build for " + projectPath + "\n" + getThreadTrace(Thread.currentThread(), 50));
    }
    runCommand(() -> {
      Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> pair = takePreloadedProcess(projectPath);
      if (pair == null) {
        return;
      }

      final RequestFuture<PreloadedProcessMessageHandler> future = pair.first;
      final OSProcessHandler processHandler = pair.second;
      myMessageDispatcher.cancelSession(future.getRequestID());
      // waiting for preloaded process from project's task queue guarantees no build is started for this project
      // until this one gracefully exits and closes all its storages
      getProjectData(projectPath).taskQueue.execute(() -> {
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
      });
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
  public TaskFuture<?> scheduleBuild(
    final Project project, final boolean isRebuild, final boolean isMake,
    final boolean onlyCheckUpToDate, final List<TargetTypeBuildScope> scopes,
    final Collection<String> paths,
    final Map<String, String> userData, final DefaultMessageHandler messageHandler) {

    final String projectPath = getProjectPath(project);
    final boolean isAutomake = messageHandler instanceof AutoMakeMessageHandler;
    final BuilderMessageHandler handler = new NotifyingMessageHandler(project, messageHandler, isAutomake);
    WSLDistribution wslDistribution = findWSLDistributionForBuild(project);
    try {
      ensureListening(wslDistribution != null ? wslDistribution.getHostIpAddress() : InetAddress.getLoopbackAddress());
    }
    catch (Exception e) {
      final UUID sessionId = UUID.randomUUID(); // the actual session did not start, use random UUID
      handler.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), null));
      handler.sessionTerminated(sessionId);
      return null;
    }
    Function<String, String> pathMapper = wslDistribution != null ? wslDistribution::getWslPath : Function.identity();

    final DelegateFuture _future = new DelegateFuture();
    // by using the same queue that processes events we ensure that
    // the build will be aware of all events that have happened before this request
    runCommand(() -> {
      final Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> preloaded = takePreloadedProcess(projectPath);
      final RequestFuture<PreloadedProcessMessageHandler> preloadedFuture = Pair.getFirst(preloaded);
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

      final RequestFuture<? extends BuilderMessageHandler> future = usingPreloadedProcess? preloadedFuture : new RequestFuture<>(handler, sessionId, new CancelBuildSessionAction<>());
      // futures we need to wait for: either just "future" or both "future" and "buildFuture" below
      List<TaskFuture<?>> delegatesToWait = Collections.singletonList(future);

      if (!usingPreloadedProcess && (future.isCancelled() || project.isDisposed())) {
        // in case of preloaded process the process was already running, so the handler will be notified upon process termination
        handler.sessionTerminated(sessionId);
        future.setDone();
      }
      else {
        final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals =
          CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.newBuilder().setGlobalOptionsPath(pathMapper.apply(PathManager.getOptionsPath()))
            .build();
        CmdlineRemoteProto.Message.ControllerMessage.FSEvent currentFSChanges;
        final ExecutorService projectTaskQueue;
        final boolean needRescan;
        synchronized (myProjectDataMap) {
          ProjectData data = getProjectData(projectPath);
          if (isRebuild) {
            data.dropChanges();
          }
          if (IS_UNIT_TEST_MODE) {
            LOG.info("Scheduling build for " +
                     projectPath +
                     "; CHANGED: " +
                     new HashSet<>(convertToStringPaths(data.myChanged)) +
                     "; DELETED: " +
                     new HashSet<>(convertToStringPaths(data.myDeleted)));
          }
          needRescan = data.getAndResetRescanFlag();
          currentFSChanges = needRescan ? null : data.createNextEvent(wslPathMapper(wslDistribution));
          if (LOG.isDebugEnabled()) {
            LOG.debug("Sending to starting build, ordinal=" + (currentFSChanges == null ? null : currentFSChanges.getOrdinal()));
          }
          if (!needRescan && myProjectDataMap.size() == 1) {
            InternedPath.clearCache();
          }
          projectTaskQueue = data.taskQueue;
        }

        String mappedProjectPath = pathMapper.apply(projectPath);
        List<String> mappedPaths = ContainerUtil.map(paths, (p) -> pathMapper.apply(p));
        final CmdlineRemoteProto.Message.ControllerMessage params;
        if (isRebuild) {
          params = CmdlineProtoUtil.createBuildRequest(mappedProjectPath, scopes, Collections.emptyList(), userData, globals, null, null);
        }
        else if (onlyCheckUpToDate) {
          params = CmdlineProtoUtil.createUpToDateCheckRequest(mappedProjectPath, scopes, mappedPaths, userData, globals, currentFSChanges);
        }
        else {
          params = CmdlineProtoUtil.createBuildRequest(mappedProjectPath, scopes, isMake ? Collections.emptyList() : mappedPaths, userData, globals, currentFSChanges,
                                                       isMake ? CompilerCacheConfigurator.INSTANCE.getCacheDownloadSettings(project) : null);
        }
        if (!usingPreloadedProcess) {
          myMessageDispatcher.registerBuildMessageHandler(future, params);
        }

        try {
          Future<?> buildFuture = projectTaskQueue.submit(() -> {
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
                    ApplicationManager.getApplication().invokeAndWait(project::save);
                  }
                  catch (Throwable e) {
                    LOG.info(e);
                  }
                }

                processHandler = launchBuildProcess(project, sessionId, false, messageHandler.getProgressIndicator());
                errorsOnLaunch = new StringBuffer();
                processHandler.addProcessListener(new StdOutputCollector((StringBuffer)errorsOnLaunch));
                processHandler.startNotify();
              }

              Integer debugPort = processHandler.getUserData(COMPILER_PROCESS_DEBUG_PORT);
              if (debugPort != null) {
                messageHandler.handleCompileMessage(
                  sessionId, CmdlineProtoUtil.createCompileProgressMessageResponse("Build: waiting for debugger connection on port " + debugPort //NON-NLS
                ).getCompileMessage());
                // additional support for debugger auto-attach feature
                //noinspection UseOfSystemOutOrSystemErr
                System.out.println("Build: Listening for transport dt_socket at address: " + debugPort.intValue()); //NON-NLS
              }

              while (!processHandler.waitFor()) {
                LOG.info("processHandler.waitFor() returned false for session " + sessionId + ", continue waiting");
              }

              final int exitValue = processHandler.getProcess().exitValue();
              if (exitValue != 0) {
                @Nls
                final StringBuilder msg = new StringBuilder();
                msg.append(JavaCompilerBundle.message("abnormal.build.process.termination")).append(": ");
                if (errorsOnLaunch != null && errorsOnLaunch.length() > 0) {
                  msg.append("\n").append(errorsOnLaunch);
                  if (StringUtil.contains(errorsOnLaunch, "java.lang.NoSuchMethodError")) {
                    msg.append(
                      "\nThe error may be caused by JARs in Java Extensions directory which conflicts with libraries used by the external build process.")
                      .append(
                        "\nTry adding -Djava.ext.dirs=\"\" argument to 'Build process VM options' in File | Settings | Build, Execution, Deployment | Compiler to fix the problem.");
                  }
                  else if (StringUtil.contains(errorsOnLaunch, "io.netty.channel.ConnectTimeoutException") && wslDistribution != null) {
                    msg.append(JavaCompilerBundle.message("wsl.network.connection.failure"));
                  }
                }
                else {
                  msg.append(JavaCompilerBundle.message("unknown.build.process.error"));
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

              if (isProcessPreloadingEnabled(project) && !onlyCheckUpToDate) {
                runCommand(() -> {
                  if (!myPreloadedBuilds.containsKey(projectPath)) {
                    try {
                      myPreloadedBuilds.put(projectPath, launchPreloadedBuildProcess(project, projectTaskQueue));
                    }
                    catch (Throwable e) {
                      LOG.info("Error pre-loading build process for project " + projectPath, e);
                    }
                  }
                });
              }
            }
          });
          delegatesToWait = Arrays.asList(future, new TaskFutureAdapter<>(buildFuture));
        }
        catch (Throwable e) {
          handleProcessExecutionFailure(sessionId, e);
        }
      }
      boolean set = _future.setDelegates(delegatesToWait);
      assert set;
    });

    return _future;
  }

  private boolean isProcessPreloadingEnabled(Project project) {
    // automatically disable process preloading when debugging or testing
    if (IS_UNIT_TEST_MODE || !Registry.is("compiler.process.preload") || myBuildProcessDebuggingEnabled) {
      return false;
    }
    if (project.isDisposed()) {
      return true;
    }

    for (BuildProcessParametersProvider provider : BuildProcessParametersProvider.EP_NAME.getExtensions(project)) {
      if (!provider.isProcessPreloadingEnabled()) {
        return false;
      }
    }
    return true;
  }

  private void notifySessionTerminationIfNeeded(@NotNull UUID sessionId, @Nullable Throwable execFailure) {
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

  private void handleProcessExecutionFailure(@NotNull UUID sessionId, Throwable e) {
    final BuilderMessageHandler unregistered = myMessageDispatcher.unregisterBuildMessageHandler(sessionId);
    if (unregistered != null) {
      unregistered.handleFailure(sessionId, CmdlineProtoUtil.createFailure(e.getMessage(), e));
      unregistered.sessionTerminated(sessionId);
    }
  }

  private @NotNull ProjectData getProjectData(@NotNull String projectPath) {
    synchronized (myProjectDataMap) {
      return myProjectDataMap.computeIfAbsent(projectPath, __ -> {
        return new ProjectData(SequentialTaskExecutor.createSequentialApplicationPoolExecutor("BuildManager Pool"));
      });
    }
  }

  private synchronized int ensureListening(InetAddress inetAddress) {
    for (ListeningConnection connection : myListeningConnections) {
      if (connection.myAddress.equals(inetAddress)) {
        return connection.myListenPort;
      }
    }
    return startListening(inetAddress);
  }

  @Override
  public void dispose() {
    stopListening();
  }

  @NotNull
  public static Pair<Sdk, JavaSdkVersion> getBuildProcessRuntimeSdk(@NotNull Project project) {
    return getRuntimeSdk(project, 8);
  }

  @NotNull
  public static Pair<Sdk, JavaSdkVersion> getJavacRuntimeSdk(@NotNull Project project) {
    return getRuntimeSdk(project, 6);
  }

  private static @NotNull Pair<Sdk, JavaSdkVersion> getRuntimeSdk(@NotNull Project project, int oldestPossibleVersion) {
    final Map<Sdk, Integer> candidates = new HashMap<>();
    Consumer<Sdk> addSdk = sdk -> {
      if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
        final Integer count = candidates.putIfAbsent(sdk, 1);
        if (count != null) {
          candidates.put(sdk, count + 1);
        }
      }
    };

    addSdk.accept(ProjectRootManager.getInstance(project).getProjectSdk());
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      addSdk.accept(ModuleRootManager.getInstance(module).getSdk());
    }

    final JavaSdk javaSdkType = JavaSdk.getInstance();

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    if (configuration.getJavacCompiler().equals(configuration.getDefaultCompiler()) && JavacConfiguration.getOptions(project, JavacConfiguration.class).PREFER_TARGET_JDK_COMPILER) {
      // for javac, if compiler from associated SDK instead of cross-compilation is preferred, use a different policy:
      // select the most frequently used jdk from the sdks that are associated with the project, but not older than <oldestPossibleVersion>
      // this policy attempts to compile as much modules as possible without spawning a separate javac process

      final List<Pair<Sdk, JavaSdkVersion>> sortedSdks = candidates.entrySet().stream()
        .sorted(Map.Entry.<Sdk, Integer>comparingByValue().reversed())
        .map(entry -> Pair.create(entry.getKey(), JavaVersion.tryParse(entry.getKey().getVersionString())))
        .filter(p -> p.second != null && p.second.isAtLeast(oldestPossibleVersion))
        .map(p -> Pair.create(p.first, JavaSdkVersion.fromJavaVersion(p.second)))
        .filter(p -> p.second != null)
        .collect(Collectors.toList());

      if (!sortedSdks.isEmpty()) {
        // first try to find most used JDK of version 9 and newer => JRT FS support will be needed
        final Pair<Sdk, JavaSdkVersion> sdk9_plus = ContainerUtil.find(sortedSdks, p -> p.second.isAtLeast(JavaSdkVersion.JDK_1_9));
        return sdk9_plus != null? sdk9_plus : sortedSdks.iterator().next(); // get the most used
      }
    }

    // now select the latest version from the sdks that are used in the project, but not older than <oldestPossibleVersion>
    return candidates.keySet().stream()
      .map(sdk -> Pair.create(sdk, JavaVersion.tryParse(sdk.getVersionString())))
      .filter(p -> p.second != null && p.second.isAtLeast(oldestPossibleVersion))
      .max(Pair.comparingBySecond())
      .map(p -> Pair.create(p.first, JavaSdkVersion.fromJavaVersion(p.second)))
      .filter(p -> p.second != null)
      .orElseGet(() -> {
        Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
        return Pair.create(internalJdk, javaSdkType.getVersion(internalJdk));
      });
  }

  private Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>> launchPreloadedBuildProcess(final Project project, ExecutorService projectTaskQueue) {

    // launching build process from projectTaskQueue ensures that no other build process for this project is currently running
    return projectTaskQueue.submit(() -> {
      if (project.isDisposed()) {
        return null;
      }
      final RequestFuture<PreloadedProcessMessageHandler> future =
        new RequestFuture<>(new PreloadedProcessMessageHandler(), UUID.randomUUID(),
                            new CancelBuildSessionAction<>());
      try {
        myMessageDispatcher.registerBuildMessageHandler(future, null);
        final OSProcessHandler processHandler = launchBuildProcess(project, future.getRequestID(), true, null);
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
    });
  }

  @Nullable
  private static WSLDistribution findWSLDistributionForBuild(@NotNull Project project) {
    final Pair<Sdk, JavaSdkVersion> pair = getBuildProcessRuntimeSdk(project);
    final Sdk projectJdk = pair.first;
    final JavaSdkType projectJdkType = (JavaSdkType)projectJdk.getSdkType();
    return WslPath.getDistributionByWindowsUncPath(projectJdkType.getVMExecutablePath(projectJdk));
  }

  private OSProcessHandler launchBuildProcess(@NotNull Project project, @NotNull UUID sessionId, boolean requestProjectPreload, @Nullable ProgressIndicator progressIndicator) throws ExecutionException {
    String compilerPath = null;
    final String vmExecutablePath;
    JavaSdkVersion sdkVersion = null;

    final String forcedCompiledJdkHome = Registry.stringValue(COMPILER_PROCESS_JDK_PROPERTY);

    if (Strings.isEmptyOrSpaces(forcedCompiledJdkHome)) {
      // choosing sdk with which the build process should be run
      final Pair<Sdk, JavaSdkVersion> pair = getBuildProcessRuntimeSdk(project);
      final Sdk projectJdk = pair.first;
      sdkVersion = pair.second;

      final JavaSdkType projectJdkType = (JavaSdkType)projectJdk.getSdkType();

      // validate tools.jar presence for jdk8 and older
      if (!JavaSdkUtil.isJdkAtLeast(projectJdk, JavaSdkVersion.JDK_1_9)) {
        final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
        if (FileUtil.pathsEqual(projectJdk.getHomePath(), internalJdk.getHomePath())) {
          // important: because internal JDK can be either JDK or JRE,
          // this is the most universal way to obtain tools.jar path in this particular case
          final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
          if (systemCompiler == null) {
            //temporary workaround for IDEA-169747
            try {
              compilerPath = ClasspathBootstrap.getResourcePath(Class.forName("com.sun.tools.javac.api.JavacTool", false, BuildManager.class.getClassLoader()));
            }
            catch (Throwable t) {
              LOG.info(t);
              compilerPath = null;
            }
            if (compilerPath == null) {
              throw new ExecutionException(JavaCompilerBundle.message("build.process.no.javac.found"));
            }
          }
          else {
            compilerPath = ClasspathBootstrap.getResourcePath(systemCompiler.getClass());
          }
        }
        else {
          compilerPath = projectJdkType.getToolsPath(projectJdk);
          if (compilerPath == null) {
            throw new ExecutionException(JavaCompilerBundle.message("build.process.no.javac.path.found", projectJdk.getName(), projectJdk.getHomePath()));
          }
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

    BuildCommandLineBuilder cmdLine;
    WslPath wslPath = WslPath.parseWindowsUncPath(vmExecutablePath);
    if (wslPath != null) {
      cmdLine = new WslBuildCommandLineBuilder(project, wslPath.getDistribution(), wslPath.getLinuxPath(), progressIndicator);
    }
    else {
      cmdLine = new LocalBuildCommandLineBuilder(vmExecutablePath);
    }
    int listenPort = ensureListening(cmdLine.getListenAddress());

    boolean isProfilingMode = false;
    String userDefinedHeapSize = null;
    final List<String> userAdditionalOptionsList = new SmartList<>();
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

    final int localHeapSize = config.COMPILER_PROCESS_HEAP_SIZE;
    if (localHeapSize > 0) {
      cmdLine.addParameter("-Xmx" + localHeapSize + "m");
    }
    else if (userDefinedHeapSize != null) {
      cmdLine.addParameter(userDefinedHeapSize);
    }
    else {
      final int heapSize = projectConfig.getBuildProcessHeapSize(JavacConfiguration.getOptions(project, JavacConfiguration.class).MAXIMUM_HEAP_SIZE);
      cmdLine.addParameter("-Xmx" + heapSize + "m");
    }

    cmdLine.addParameter("-Djava.awt.headless=true");
    if (sdkVersion != null) {
      if (sdkVersion.compareTo(JavaSdkVersion.JDK_1_9) < 0) {
        //-Djava.endorsed.dirs is not supported in JDK 9+, may result in abnormal process termination
        cmdLine.addParameter("-Djava.endorsed.dirs=\"\""); // turn off all jre customizations for predictable behaviour
      }
      if (sdkVersion.isAtLeast(JavaSdkVersion.JDK_16)) {
        // enable javac-related reflection tricks in JPS
        ClasspathBootstrap.configureReflectionOpenPackages(p -> cmdLine.addParameter(p));
      }
    }
    if (IS_UNIT_TEST_MODE) {
      cmdLine.addParameter("-Dtest.mode=true");
    }

    if (requestProjectPreload) {
      cmdLine.addPathParameter("-Dpreload.project.path=", FileUtil.toCanonicalPath(getProjectPath(project)));
      cmdLine.addPathParameter("-Dpreload.config.path=", FileUtil.toCanonicalPath(PathManager.getOptionsPath()));
    }

    if (ProjectUtilCore.isExternalStorageEnabled(project)) {
      cmdLine.addPathParameter("-D" + GlobalOptions.EXTERNAL_PROJECT_CONFIG + "=", ProjectUtil.getExternalConfigurationDir(project));
    }

    cmdLine.addParameter("-D" + GlobalOptions.COMPILE_PARALLEL_OPTION + "=" + projectConfig.isParallelCompilationEnabled());
    if (projectConfig.isParallelCompilationEnabled()) {
      if (!Registry.is("compiler.automake.allow.parallel", true)) {
        cmdLine.addParameter("-D" + GlobalOptions.ALLOW_PARALLEL_AUTOMAKE_OPTION + "=false");
      }
    }
    cmdLine.addParameter("-D" + GlobalOptions.REBUILD_ON_DEPENDENCY_CHANGE_OPTION + "=" + config.REBUILD_ON_DEPENDENCY_CHANGE);

    if (Registry.is("compiler.build.report.statistics")) {
      cmdLine.addParameter("-D" + GlobalOptions.REPORT_BUILD_STATISTICS + "=true");
    }
    if (Registry.is("compiler.natural.int.multimap.impl")) {  // todo: temporary flag to evaluate experimental multimap implementation
      cmdLine.addParameter("-Djps.mappings.natural.int.multimap.impl=true");
    }

    // third party libraries tweaks
    cmdLine.addParameter("-Djdt.compiler.useSingleThread=true"); // always run eclipse compiler in single-threaded mode
    cmdLine.addParameter("-Daether.connector.resumeDownloads=false"); // always re-download maven libraries if partially downloaded
    cmdLine.addParameter("-Dio.netty.initialSeedUniquifier=" + ThreadLocalRandom.getInitialSeedUniquifier()); // this will make netty initialization faster on some systems

    for (String option : userAdditionalOptionsList) {
      cmdLine.addParameter(option);
    }

    cmdLine.setupAdditionalVMOptions();

    Path hostWorkingDirectory = cmdLine.getHostWorkingDirectory();
    if (!FileUtil.createDirectory(hostWorkingDirectory.toFile())) {
      LOG.warn("Failed to create build working directory " + hostWorkingDirectory);
    }

    if (isProfilingMode) {
      try {
        YourKitProfilerService yourKitProfilerService = ApplicationManager.getApplication().getService(YourKitProfilerService.class);
        if (yourKitProfilerService == null) {
          throw new IOException("Performance Plugin is missing or disabled");
        }
        yourKitProfilerService.copyYKLibraries(hostWorkingDirectory);
        String buildSnapshotPath = System.getProperty("build.snapshots.path");
        String parameters;
        if (buildSnapshotPath != null) {
          parameters = "-agentpath:" +
                       cmdLine.getYjpAgentPath(yourKitProfilerService) +
                       "=disablealloc,delay=10000,sessionname=ExternalBuild,dir=" +
                       buildSnapshotPath;
        }
        else {
          parameters =
            "-agentpath:" + cmdLine.getYjpAgentPath(yourKitProfilerService) + "=disablealloc,delay=10000,sessionname=ExternalBuild";
        }
        cmdLine.addParameter(parameters);
        showSnapshotNotificationAfterFinish(project);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }

    // debugging
    int debugPort = -1;
    if (myBuildProcessDebuggingEnabled) {
      debugPort = Registry.intValue("compiler.process.debug.port");
      if (debugPort <= 0) {
        try {
          debugPort = NetUtils.findAvailableSocketPort();
        }
        catch (IOException e) {
          throw new ExecutionException(JavaCompilerBundle.message("build.process.no.free.debug.port"), e);
        }
      }
      if (debugPort > 0) {
        cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
        cmdLine.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
      }
    }

    // portable caches
    if (Registry.is("compiler.process.use.portable.caches")
        && GitRepositoryUtil.isIntelliJRepository(project)
        && CompilerCacheStartupActivity.isLineEndingsConfiguredCorrectly()) {
      //cmdLine.addParameter("-Didea.resizeable.file.truncate.on.close=true");
      //cmdLine.addParameter("-Dkotlin.jps.non.caching.storage=true");
      cmdLine.addParameter("-D" + ProjectStamps.PORTABLE_CACHES_PROPERTY + "=true");
    }

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    cmdLine.setCharset(mySystemCharset);
    cmdLine.addParameter("-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + mySystemCharset.name());
    for (String name : INHERITED_IDE_VM_OPTIONS) {
      final String value = System.getProperty(name);
      if (value != null) {
        cmdLine.addParameter("-D" + name + "=" + value);
      }
    }
    final DynamicBundle.LanguageBundleEP languageBundle = DynamicBundle.findLanguageBundle();
    if (languageBundle != null) {
      final PluginDescriptor pluginDescriptor = languageBundle.pluginDescriptor;
      final ClassLoader loader = pluginDescriptor == null ? null : pluginDescriptor.getClassLoader();
      final String bundlePath = loader == null ? null : PathManager.getResourceRoot(loader, "META-INF/plugin.xml");
      if (bundlePath != null) {
        cmdLine.addParameter("-D"+ GlobalOptions.LANGUAGE_BUNDLE + "=" + FileUtil.toSystemIndependentName(bundlePath));
      }
    }
    cmdLine.addPathParameter("-D" + PathManager.PROPERTY_HOME_PATH + "=", FileUtil.toSystemIndependentName(PathManager.getHomePath()));
    cmdLine.addPathParameter("-D" + PathManager.PROPERTY_CONFIG_PATH + "=", FileUtil.toSystemIndependentName(PathManager.getConfigPath()));
    cmdLine.addPathParameter("-D" + PathManager.PROPERTY_PLUGINS_PATH + "=", FileUtil.toSystemIndependentName(PathManager.getPluginsPath()));

    cmdLine.addPathParameter("-D" + GlobalOptions.LOG_DIR_OPTION + "=", FileUtil.toSystemIndependentName(getBuildLogDirectory().getAbsolutePath()));
    if (myFallbackSdkHome != null && myFallbackSdkVersion != null) {
      cmdLine.addPathParameter("-D" + GlobalOptions.FALLBACK_JDK_HOME + "=", myFallbackSdkHome);
      cmdLine.addParameter("-D" + GlobalOptions.FALLBACK_JDK_VERSION + "=" + myFallbackSdkVersion);
    }
    cmdLine.addParameter("-Dio.netty.noUnsafe=true");


    final File projectSystemRoot = getProjectSystemDirectory(project);
    if (projectSystemRoot != null) {
      cmdLine.addPathParameter("-Djava.io.tmpdir=", FileUtil.toSystemIndependentName(projectSystemRoot.getPath()) + "/" + TEMP_DIR_NAME);
    }

    for (BuildProcessParametersProvider provider : BuildProcessParametersProvider.EP_NAME.getExtensions(project)) {
      for (String arg : provider.getVMArguments()) {
        cmdLine.addParameter(arg);
      }

      for (Pair<String, Path> parameter : provider.getPathParameters()) {
        cmdLine.addPathParameter(parameter.getFirst(), cmdLine.copyPathToTargetIfRequired(parameter.getSecond()));
      }
    }

    @SuppressWarnings("UnnecessaryFullyQualifiedName")
    final Class<?> launcherClass = org.jetbrains.jps.cmdline.Launcher.class;

    final List<String> launcherCp = new ArrayList<>();
    launcherCp.add(ClasspathBootstrap.getResourcePath(launcherClass));
    launcherCp.addAll(BuildProcessClasspathManager.getLauncherClasspath(project));
    if (compilerPath != null) {   // can be null in case of jdk9
      launcherCp.add(compilerPath);
    }

    boolean includeBundledEcj = shouldIncludeEclipseCompiler(projectConfig);
    File customEcjPath = null;
    if (includeBundledEcj) {
      final String path = EclipseCompilerConfiguration.getOptions(project, EclipseCompilerConfiguration.class).ECJ_TOOL_PATH;
      if (!StringUtil.isEmptyOrSpaces(path)) {
        customEcjPath = new File(path);
        if (customEcjPath.exists()) {
          includeBundledEcj = false;
        }
        else {
          throw new ExecutionException(JavaCompilerBundle.message("build.process.ecj.path.does.not.exist", customEcjPath.getAbsolutePath()));
          //customEcjPath = null;
        }
      }
    }

    ClasspathBootstrap.appendJavaCompilerClasspath(launcherCp, includeBundledEcj);
    if (customEcjPath != null) {
      launcherCp.add(customEcjPath.getAbsolutePath());
    }

    cmdLine.addParameter("-classpath");
    cmdLine.addClasspathParameter(launcherCp, Collections.emptyList());

    cmdLine.addParameter(launcherClass.getName());

    final List<String> cp = myClasspathManager.getBuildProcessClasspath(project);
    cmdLine.addClasspathParameter(cp, isProfilingMode ? Collections.singletonList("yjp-controller-api-redist.jar") : Collections.emptyList());

    for (BuildProcessParametersProvider buildProcessParametersProvider : BuildProcessParametersProvider.EP_NAME.getExtensions(project)) {
      for (String path : buildProcessParametersProvider.getAdditionalPluginPaths()) {
        cmdLine.copyPathToTargetIfRequired(Paths.get(path));
      }
    }

    cmdLine.addParameter(BuildMain.class.getName());
    cmdLine.addParameter(cmdLine.getHostIp());
    cmdLine.addParameter(Integer.toString(listenPort));
    cmdLine.addParameter(sessionId.toString());

    cmdLine.addParameter(cmdLine.getWorkingDirectory());

    boolean lowPriority = Registry.is(LOW_PRIORITY_REGISTRY_KEY);
    if (SystemInfo.isUnix && lowPriority) {
      cmdLine.setUnixProcessPriority(10);
    }

    try {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).beforeBuildProcessStarted(project, sessionId);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    final OSProcessHandler processHandler = new OSProcessHandler(cmdLine.buildCommandLine()) {
      @Override
      protected boolean shouldDestroyProcessRecursively() {
        return true;
      }

      @NotNull
      @Override
      protected BaseOutputReader.Options readerOptions() {
        return BaseOutputReader.Options.BLOCKING;
      }
    };
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        // re-translate builder's output to idea.log
        final String text = event.getText();
        if (!StringUtil.isEmptyOrSpaces(text)) {
          if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("BUILDER_PROCESS [" + outputType + "]: " + text.trim());
            }
          }
          else {
            LOG.info("BUILDER_PROCESS [" + outputType + "]: " + text.trim());
          }
        }
      }
    });
    if (debugPort > 0) {
      processHandler.putUserData(COMPILER_PROCESS_DEBUG_PORT, debugPort);
    }

    if (SystemInfo.isWindows && lowPriority) {
      final WinProcess winProcess = new WinProcess(OSProcessUtil.getProcessID(processHandler.getProcess()));
      winProcess.setPriority(Priority.IDLE);
    }

    return processHandler;
  }

  private static void showSnapshotNotificationAfterFinish(@NotNull Project project) {
    MessageBusConnection busConnection = project.getMessageBus().connect();
    busConnection.subscribe(BuildManagerListener.TOPIC, new BuildManagerListener() {
      @Override
      public void buildFinished(@NotNull Project project, @NotNull UUID sessionId, boolean isAutomake) {
        busConnection.disconnect();
        final Notification notification = new Notification(
          "Build Profiler",
          JavaCompilerBundle.message("notification.title.cpu.snapshot.build.has.been.captured"),
          "", NotificationType.INFORMATION
        );
        notification.addAction(new DumbAwareAction(JavaCompilerBundle.message("action.show.snapshot.location.text")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            RevealFileAction.openDirectory(new File(SystemProperties.getUserHome(), "snapshots"));
            notification.expire();
          }
        });
        Notifications.Bus.notify(notification, project);
      }
    });
  }

  private static boolean shouldIncludeEclipseCompiler(CompilerConfiguration config) {
    if (config instanceof CompilerConfigurationImpl) {
      final BackendCompiler javaCompiler = ((CompilerConfigurationImpl)config).getDefaultCompiler();
      final String compilerId = javaCompiler != null ? javaCompiler.getId() : null;
      return JavaCompilers.ECLIPSE_ID.equals(compilerId) || JavaCompilers.ECLIPSE_EMBEDDED_ID.equals(compilerId);
    }
    return true;
  }

  /**
   * @deprecated use {@link #getBuildSystemDirectory(Project)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  @NotNull
  public Path getBuildSystemDirectory() {
    return LocalBuildCommandLineBuilder.getLocalBuildSystemDirectory();
  }

  @NotNull
  public Path getBuildSystemDirectory(Project project) {
    final WslPath wslPath = WslPath.parseWindowsUncPath(getProjectPath(project));
    if (wslPath != null) {
      Path buildDir = WslBuildCommandLineBuilder.getWslBuildSystemDirectory(wslPath.getDistribution());
      if (buildDir != null) {
        return buildDir;
      }
    }
    return LocalBuildCommandLineBuilder.getLocalBuildSystemDirectory();
  }

  @NotNull
  public static File getBuildLogDirectory() {
    return new File(PathManager.getLogPath(), "build-log");
  }

  @Nullable
  public File getProjectSystemDirectory(Project project) {
    String projectPath = getProjectPath(project);
    WslPath wslPath = WslPath.parseWindowsUncPath(projectPath);
    Function<String, Integer> hashFunction;
    if (wslPath == null) {
      hashFunction = String::hashCode;
    }
    else {
      hashFunction = s -> wslPath.getDistribution().getWslPath(s).hashCode();
    }
    return Utils.getDataStorageRoot(getBuildSystemDirectory(project).toFile(), projectPath, hashFunction);
  }

  private static File getUsageFile(@NotNull File projectSystemDir) {
    return new File(projectSystemDir, "ustamp");
  }

  private static void updateUsageFile(@Nullable Project project, @NotNull File projectSystemDir) {
    File usageFile = getUsageFile(projectSystemDir);
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
      List<String> lines = FileUtil.loadLines(usageFile, StandardCharsets.UTF_8.name());
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

  private void stopListening() {
    for (ListeningConnection connection : myListeningConnections) {
      connection.myChannelRegistrar.close();
    }
    myListeningConnections.clear();
  }

  private int startListening(InetAddress address) {
    ListeningConnection listeningConnection = new ListeningConnection(address);
    BuiltInServerManager builtInServerManager = BuiltInServerManager.getInstance();
    builtInServerManager.waitForStart();
    ServerBootstrap bootstrap = ((BuiltInServerManagerImpl)builtInServerManager).createServerBootstrap();
    bootstrap.childHandler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(@NotNull Channel channel) {
        channel.pipeline().addLast(listeningConnection.myChannelRegistrar,
                                   new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(CmdlineRemoteProto.Message.getDefaultInstance()),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   myMessageDispatcher);
      }
    });
    Channel serverChannel = bootstrap.bind(address, 0).syncUninterruptibly().channel();
    listeningConnection.myChannelRegistrar.setServerChannel(serverChannel, false);
    int port = ((InetSocketAddress)serverChannel.localAddress()).getPort();
    listeningConnection.myListenPort = port;
    myListeningConnections.add(listeningConnection);
    return port;
  }

  public boolean isBuildProcessDebuggingEnabled() {
    return myBuildProcessDebuggingEnabled;
  }

  public void setBuildProcessDebuggingEnabled(boolean buildProcessDebuggingEnabled) {
    myBuildProcessDebuggingEnabled = buildProcessDebuggingEnabled;
    if (myBuildProcessDebuggingEnabled) {
      cancelAllPreloadedBuilds();
    }
  }

  private abstract class BuildManagerPeriodicTask implements Runnable {
    private final Alarm myAlarm;
    private final AtomicBoolean myInProgress = new AtomicBoolean(false);
    private final Runnable myTaskRunnable = () -> {
      try {
        runTask();
      }
      finally {
        myInProgress.set(false);
      }
    };

    BuildManagerPeriodicTask() {
      myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, BuildManager.this);
    }

    final void schedule() {
      schedule(false);
    }

    private void schedule(boolean increasedDelay) {
      cancelPendingExecution();
      myAlarm.addRequest(this, Math.max(100, increasedDelay ? 10 * getDelay() : getDelay()));
    }

    void cancelPendingExecution() {
      myAlarm.cancelAllRequests();
    }

    protected boolean shouldPostpone() {
      return false;
    }

    protected abstract int getDelay();

    protected abstract void runTask();

    @Override
    public final void run() {
      final boolean shouldPostponeAllTasks = HeavyProcessLatch.INSTANCE.isRunning() || mySuspendBackgroundTasksCounter.get() > 0;
      if (!shouldPostponeAllTasks && !shouldPostpone() && !myInProgress.getAndSet(true)) {
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
        schedule(shouldPostponeAllTasks);
      }
    }
  }

  private static final class NotifyingMessageHandler extends DelegatingMessageHandler {
    private final Project myProject;
    private final BuilderMessageHandler myDelegateHandler;
    private final boolean myIsAutomake;

    NotifyingMessageHandler(@NotNull Project project, @NotNull BuilderMessageHandler delegateHandler, final boolean isAutomake) {
      myProject = project;
      myDelegateHandler = delegateHandler;
      myIsAutomake = isAutomake;
    }

    @Override
    protected BuilderMessageHandler getDelegateHandler() {
      return myDelegateHandler;
    }

    @Override
    public void buildStarted(@NotNull UUID sessionId) {
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
    public void sessionTerminated(@NotNull UUID sessionId) {
      try {
        super.sessionTerminated(sessionId);
      }
      finally {
        try {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).buildFinished(myProject, sessionId, myIsAutomake);
        }
        catch (AlreadyDisposedException e) {
          LOG.warn(e);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  private static final class StdOutputCollector extends ProcessAdapter {
    private final Appendable myOutput;
    private int myStoredLength;
    StdOutputCollector(@NotNull Appendable outputSink) {
      myOutput = outputSink;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      String text;

      synchronized (this) {
        if (myStoredLength > 16384) {
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

  static final class BuildManagerStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return;
      }

      MessageBusConnection connection = project.getMessageBus().connect();
      connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        @Override
        public void rootsChanged(@NotNull ModuleRootEvent event) {
          Object source = event.getSource();
          if (source instanceof Project && !((Project)source).isDefault()) {
            getInstance().clearState((Project)source);
          }
        }
      });
      connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
        @Override
        public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
          getInstance().cancelAutoMakeTasks(env.getProject()); // make sure to cancel all automakes waiting in the build queue
        }

        @Override
        public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
          // make sure to cancel all automakes added to the build queue after processStaring and before this event
          getInstance().cancelAutoMakeTasks(env.getProject());
        }

        @Override
        public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
          // augmenting reaction to processTerminated(): in case any automakes were canceled before process start
          getInstance().scheduleAutoMake();
        }

        @Override
        public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
          getInstance().scheduleAutoMake();
        }
      });
      connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
        private final Set<String> myRootsToRefresh = CollectionFactory.createFilePathSet();

        @Override
        public void automakeCompilationFinished(int errors, int warnings, @NotNull CompileContext compileContext) {
          if (!compileContext.getProgressIndicator().isCanceled()) {
            refreshSources(compileContext);
          }
        }

        @Override
        public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
          refreshSources(compileContext);
        }

        private void refreshSources(CompileContext compileContext) {
          if (project.isDisposed()) {
            return;
          }
          final Set<String> candidates = CollectionFactory.createFilePathSet();
          synchronized (myRootsToRefresh) {
            candidates.addAll(myRootsToRefresh);
            myRootsToRefresh.clear();
          }
          if (compileContext.isAnnotationProcessorsEnabled()) {
            // annotation processors may have re-generated code
            final CompilerConfiguration config = CompilerConfiguration.getInstance(project);
            for (Module module : compileContext.getCompileScope().getAffectedModules()) {
              if (config.getAnnotationProcessingConfiguration(module).isEnabled()) {
                final String productionPath = CompilerPaths.getAnnotationProcessorsGenerationPath(module, false);
                if (productionPath != null) {
                  candidates.add(productionPath);
                }
                final String testsPath = CompilerPaths.getAnnotationProcessorsGenerationPath(module, true);
                if (testsPath != null) {
                  candidates.add(testsPath);
                }
              }
            }
          }

          if (!candidates.isEmpty()) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
              if (project.isDisposed()) {
                return;
              }

              CompilerUtil.refreshOutputRoots(candidates);

              LocalFileSystem lfs = LocalFileSystem.getInstance();
              Set<VirtualFile> toRefresh = ReadAction.compute(() -> {
                if (project.isDisposed()) {
                  return Collections.emptySet();
                }
                else {
                  ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                  return candidates.stream()
                    .map(lfs::findFileByPath)
                    .filter(root -> root != null && fileIndex.isInSourceContent(root))
                    .collect(Collectors.toSet());
                }
              });

              if (!toRefresh.isEmpty()) {
                lfs.refreshFiles(toRefresh, true, true, null);
              }
            });
          }
        }

        @Override
        public void fileGenerated(@NotNull String outputRoot, @NotNull String relativePath) {
          synchronized (myRootsToRefresh) {
            myRootsToRefresh.add(outputRoot);
          }
        }
      });

      String projectPath = getProjectPath(project);
      Disposer.register(project, () -> {
        BuildManager buildManager = getInstance();
        buildManager.cancelPreloadedBuilds(projectPath);
        buildManager.myProjectDataMap.remove(projectPath);
      });

      BuildProcessParametersProvider.EP_NAME.addChangeListener(project, () -> getInstance().cancelAllPreloadedBuilds(), null);

      getInstance().runCommand(() -> {
        File projectSystemDir = getInstance().getProjectSystemDirectory(project);
        if (projectSystemDir != null) {
          updateUsageFile(project, projectSystemDir);
        }
      });
      // run automake after project opened
      getInstance().scheduleAutoMake();
    }
  }

  private final class ProjectWatcher implements ProjectManagerListener {
    private final Map<Project, MessageBusConnection> myConnections = new HashMap<>();

    @Override
    public void projectClosingBeforeSave(@NotNull Project project) {
      myAutoMakeTask.cancelPendingExecution();
      cancelAutoMakeTasks(project);
    }

    @Override
    public void projectClosing(@NotNull Project project) {
      String projectPath = Objects.requireNonNull(getProjectPath(project));
      ProgressManager.getInstance().run(new Task.Modal(project, JavaCompilerBundle.message("progress.title.cancelling.auto.make.builds"), false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myAutoMakeTask.cancelPendingExecution();
          cancelPreloadedBuilds(projectPath);
          for (TaskFuture<?> future : cancelAutoMakeTasks(getProject())) {
            future.waitFor(500, TimeUnit.MILLISECONDS);
          }
        }
      });
    }

    @Override
    public void projectClosed(@NotNull Project project) {
      if (myProjectDataMap.remove(getProjectPath(project)) != null) {
        if (myProjectDataMap.isEmpty()) {
          InternedPath.clearCache();
        }
      }
      MessageBusConnection connection = myConnections.remove(project);
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static final class ProjectData {
    @NotNull final ExecutorService taskQueue;
    private final Set<InternedPath> myChanged = new HashSet<>();
    private final Set<InternedPath> myDeleted = new HashSet<>();
    private long myNextEventOrdinal;
    private boolean myNeedRescan = true;

    private ProjectData(@NotNull ExecutorService taskQueue) {
      this.taskQueue = taskQueue;
    }

    boolean hasChanges() {
      return myNeedRescan || !myChanged.isEmpty() || !myDeleted.isEmpty();
    }

    void addChanged(Collection<String> paths) {
      if (!myNeedRescan) {
        for (String path : paths) {
          final InternedPath _path = InternedPath.create(path);
          myDeleted.remove(_path);
          myChanged.add(_path);
        }
      }
    }

    void addDeleted(Collection<String> paths) {
      if (!myNeedRescan) {
        for (String path : paths) {
          final InternedPath _path = InternedPath.create(path);
          myChanged.remove(_path);
          myDeleted.add(_path);
        }
      }
    }

    CmdlineRemoteProto.Message.ControllerMessage.FSEvent createNextEvent(Function<String, String> pathMapper) {
      final CmdlineRemoteProto.Message.ControllerMessage.FSEvent.Builder builder =
        CmdlineRemoteProto.Message.ControllerMessage.FSEvent.newBuilder();
      builder.setOrdinal(++myNextEventOrdinal);

      for (InternedPath path : myChanged) {
        builder.addChangedPaths(pathMapper.apply(path.getValue()));
      }
      myChanged.clear();

      for (InternedPath path : myDeleted) {
        builder.addDeletedPaths(pathMapper.apply(path.getValue()));
      }
      myDeleted.clear();

      return builder.build();
    }

    boolean getAndResetRescanFlag() {
      final boolean rescan = myNeedRescan;
      myNeedRescan = false;
      return rescan;
    }

    void dropChanges() {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Project build state cleared: " + getThreadTrace(Thread.currentThread(), 20));
      }
      myNeedRescan = true;
      myNextEventOrdinal = 0L;
      myChanged.clear();
      myDeleted.clear();
    }
  }

  private abstract static class InternedPath {
    private static final SLRUCache<CharSequence, CharSequence> ourNamesCache = SLRUCache.create(1024, 1024, key -> key);
    protected final CharSequence[] myPath;

    /**
     * @param path assuming system-independent path with forward slashes
     */
    InternedPath(String path) {
      final List<CharSequence> list = new ArrayList<>();
      final StringTokenizer tokenizer = new StringTokenizer(path, "/", false);
      synchronized (ourNamesCache) {
        while(tokenizer.hasMoreTokens()) {
          list.add(ourNamesCache.get(tokenizer.nextToken()));
        }
      }
      myPath = list.toArray(CharSequence[]::new);
    }

    public abstract String getValue();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InternedPath path = (InternedPath)o;

      return Arrays.equals(myPath, path.myPath);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myPath);
    }

    public static void clearCache() {
      synchronized (ourNamesCache) {
        ourNamesCache.clear();
      }
    }

    public static InternedPath create(String path) {
      return path.startsWith("/")? new XInternedPath(path) : new WinInternedPath(path);
    }
  }

  private static final class WinInternedPath extends InternedPath {
    private WinInternedPath(String path) {
      super(path);
    }

    @Override
    public String getValue() {
      if (myPath.length == 1) {
        final String name = myPath[0].toString();
        // handle case of windows drive letter
        return name.length() == 2 && name.endsWith(":")? name + "/" : name;
      }

      final StringBuilder buf = new StringBuilder();
      for (CharSequence element : myPath) {
        if (buf.length() > 0) {
          buf.append("/");
        }
        buf.append(element);
      }
      return buf.toString();
    }
  }

  private static final class XInternedPath extends InternedPath {
    private XInternedPath(String path) {
      super(path);
    }

    @Override
    public String getValue() {
      if (myPath.length > 0) {
        final StringBuilder buf = new StringBuilder();
        for (CharSequence element : myPath) {
          buf.append("/").append(element);
        }
        return buf.toString();
      }
      return "/";
    }
  }

  private static final class DelegateFuture implements TaskFuture {
    private List<TaskFuture<?>> myDelegates;
    private Boolean myRequestedCancelState;

    private synchronized @NotNull List<TaskFuture<?>> getDelegates() {
      List<TaskFuture<?>> delegates = myDelegates;
      while (delegates == null) {
        try {
          wait();
        }
        catch (InterruptedException ignored) {
        }
        delegates = myDelegates;
      }
      return delegates;
    }

    private synchronized boolean setDelegates(@NotNull List<TaskFuture<?>> delegates) {
      if (myDelegates == null) {
        try {
          myDelegates = delegates;
          if (myRequestedCancelState != null) {
            for (TaskFuture<?> delegate : delegates) {
              delegate.cancel(myRequestedCancelState);
            }
          }
        }
        finally {
          notifyAll();
        }
        return true;
      }
      return false;
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      List<TaskFuture<?>> delegates = myDelegates;
      if (delegates == null) {
        myRequestedCancelState = mayInterruptIfRunning;
        return true;
      }
      for (TaskFuture<?> delegate : getDelegates()) {
        delegate.cancel(mayInterruptIfRunning);
      }
      return isDone();
    }

    @Override
    public void waitFor() {
      for (TaskFuture<?> delegate : getDelegates()) {
        delegate.waitFor();
      }
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      for (TaskFuture<?> delegate : getDelegates()) {
        delegate.waitFor(timeout, unit);
      }
      return isDone();
    }

    @Override
    public boolean isCancelled() {
      List<TaskFuture<?>> delegates;
      synchronized (this) {
        delegates = myDelegates;
        if (delegates == null) {
          return myRequestedCancelState != null;
        }
      }
      for (TaskFuture<?> delegate : delegates) {
        if (delegate.isCancelled()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean isDone() {
      List<TaskFuture<?>> delegates;
      synchronized (this) {
        delegates = myDelegates;
        if (delegates == null) {
          return false;
        }
      }
      for (TaskFuture<?> delegate : delegates) {
        if (!delegate.isDone()) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Object get() throws InterruptedException, java.util.concurrent.ExecutionException {
      for (Future<?> delegate : getDelegates()) {
        delegate.get();
      }
      return null;
    }

    @Override
    public Object get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
      for (Future<?> delegate : getDelegates()) {
        delegate.get(timeout, unit);
      }
      return null;
    }
  }

  private class CancelBuildSessionAction<T extends BuilderMessageHandler> implements RequestFuture.CancelAction<T> {
    @Override
    public void cancel(RequestFuture<T> future) {
      myMessageDispatcher.cancelSession(future.getRequestID());
      notifySessionTerminationIfNeeded(future.getRequestID(), null);
    }
  }

  static final class MyDocumentListener implements DocumentListener {
    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (ApplicationManager.getApplication().isUnitTestMode() || !Registry.is("compiler.document.save.enabled", false)) {
        return;
      }

      Document document = e.getDocument();
      if (!FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
        return;
      }

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file != null && file.isInLocalFileSystem()) {
        getInstance().scheduleProjectSave();
      }
    }
  }
}
