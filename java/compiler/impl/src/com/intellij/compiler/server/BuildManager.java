// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

import com.intellij.DynamicBundle;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.YourKitProfilerService;
import com.intellij.compiler.cache.CompilerCacheConfigurator;
import com.intellij.compiler.cache.CompilerCacheStartupActivity;
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
import com.intellij.execution.wsl.WslProxy;
import com.intellij.ide.IdleTracker;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.l10n.LocalizationUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.*;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.*;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.registry.RegistryManagerKt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache;
import com.intellij.platform.backend.workspace.WorkspaceModelCache;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.path.EelPath;
import com.intellij.platform.eel.provider.EelNioBridgeServiceKt;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.platform.eel.provider.utils.EelPathUtils;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppJavaExecutorUtil;
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.net.NetUtils;
import com.intellij.workspaceModel.ide.impl.InternalEnvironmentNameImpl;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.internal.ThreadLocalRandom;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
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
import org.jetbrains.jps.javac.ExternalJavacProcess;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.util.Iterators;
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
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.ide.impl.ProjectUtil.getProjectForComponent;
import static com.intellij.openapi.diagnostic.InMemoryHandler.IN_MEMORY_LOGGER_ADVANCED_SETTINGS_NAME;
import static com.intellij.platform.eel.provider.EelNioBridgeServiceKt.asEelPath;
import static com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote;
import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

public final class BuildManager implements Disposable {
  public static final Key<Boolean> ALLOW_AUTOMAKE = Key.create("_allow_automake_when_process_is_active_");
  private static final Key<String> COMPILER_PROCESS_DEBUG_HOST_PORT = Key.create("_compiler_process_debug_host_port_");
  private static final Key<CharSequence> STDERR_OUTPUT = Key.create("_process_launch_errors_");
  private static final SimpleDateFormat USAGE_STAMP_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

  private static final Logger LOG = Logger.getInstance(BuildManager.class);
  private static final String COMPILER_PROCESS_JDK_PROPERTY = "compiler.process.jdk";
  public static final String SYSTEM_ROOT = "compile-server";
  public static final String TEMP_DIR_NAME = "_temp_";
  private static final String[] INHERITED_IDE_VM_OPTIONS = {
    "user.language", "user.country", "user.region", PathManager.PROPERTY_PATHS_SELECTOR, "idea.case.sensitive.fs",
    "java.net.preferIPv4Stack"
  };

  private static final String IWS_EXTENSION = ".iws";  // an instance field; in order not to access the application on loading the class
  private static final int MINIMUM_REQUIRED_JPS_BUILD_JAVA_VERSION = 11;
  private final boolean IS_UNIT_TEST_MODE;
  private static final String IPR_EXTENSION = ".ipr";
  private static final String IDEA_PROJECT_DIR_PATTERN = ".idea";
  private static final Predicate<InternedPath> PATH_FILTER =
    SystemInfo.isFileSystemCaseSensitive ?
    path -> !(path.contains(IDEA_PROJECT_DIR_PATTERN::equals) ||
              path.getName().endsWith(IWS_EXTENSION) ||
              path.getName().endsWith(IPR_EXTENSION)) :
    path -> !(path.contains(elem -> StringUtil.equalsIgnoreCase(elem, IDEA_PROJECT_DIR_PATTERN)) ||
              Strings.endsWithIgnoreCase(path.getName(), IWS_EXTENSION) ||
              Strings.endsWithIgnoreCase(path.getName(), IPR_EXTENSION));

  private static final String JPS_USE_EXPERIMENTAL_STORAGE = "jps.use.experimental.storage";

  private final String myFallbackSdkHome;
  private final String myFallbackSdkVersion;

  private final Map<TaskFuture<?>, Project> myAutomakeFutures = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, RequestFuture<?>> myBuildsInProgress = Collections.synchronizedMap(new HashMap<>());
  private final Map<String, Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>>> myPreloadedBuilds =
    Collections.synchronizedMap(new HashMap<>());
  private final BuildProcessClasspathManager myClasspathManager = new BuildProcessClasspathManager(this);
  private final CoroutineDispatcherBackedExecutor myRequestsProcessor;
  private final List<VFileEvent> myUnprocessedEvents = new ArrayList<>();
  private final CoroutineDispatcherBackedExecutor myAutomakeTrigger;
  private final Map<String, ProjectData> myProjectDataMap = Collections.synchronizedMap(new HashMap<>());
  private final AtomicInteger mySuspendBackgroundTasksCounter = new AtomicInteger(0);

  private final BuildManagerPeriodicTask myAutoMakeTask;

  private final BuildManagerPeriodicTask myDocumentSaveTask;

  private final Runnable myGCTask = () -> {
    // todo: make customizable in UI?
    final int unusedThresholdDays = Registry.intValue("compiler.build.data.unused.threshold", -1);
    if (unusedThresholdDays <= 0) {
      return;
    }

    Collection<File> systemDirs = Collections.singleton(LocalBuildCommandLineBuilder.getLocalBuildSystemDirectory().toFile());
    if (Boolean.parseBoolean(System.getProperty("compiler.build.data.clean.unused.wsl"))) {
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
        Instant now = Instant.now();
        for (File buildDataProjectDir : dirs) {
          File usageFile = getUsageFile(buildDataProjectDir);
          if (usageFile.exists()) {
            final Pair<Date, File> usageData = readUsageFile(usageFile);
            if (usageData != null) {
              final File projectFile = usageData.second;
              if (projectFile != null && !projectFile.exists() ||
                  Duration.between(usageData.first.toInstant(), now).toDays() > unusedThresholdDays) {
                LOG.info("Clearing project build data because the project does not exist or was not opened for more than " +
                         unusedThresholdDays +
                         " days: " +
                         buildDataProjectDir);
                FileUtil.delete(buildDataProjectDir);
              }
            }
          }
          else {
            updateUsageFile(null, buildDataProjectDir); // set usage stamp to start the countdown
          }
        }
      }
    }
  };

  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();

  private static final class ListeningConnection {
    private final InetAddress myAddress;
    private final ChannelRegistrar myChannelRegistrar = new ChannelRegistrar();
    private volatile int myListenPort = -1;

    private ListeningConnection(InetAddress address) {
      myAddress = address;
    }
  }

  private final List<ListeningConnection> myListeningConnections = new ArrayList<>();
  private final Map<String, WslProxy> myWslProxyCache = new HashMap<>();

  private final @NotNull Charset mySystemCharset = CharsetToolkit.getDefaultSystemCharset();
  private volatile boolean myBuildProcessDebuggingEnabled;

  public BuildManager(@NotNull CoroutineScope coroutineScope) {
    myRequestsProcessor = AppJavaExecutorUtil.createBoundedTaskExecutor("BuildManager RequestProcessor Pool", coroutineScope);
    myAutomakeTrigger = AppJavaExecutorUtil.createBoundedTaskExecutor("BuildManager Auto-Make Trigger", coroutineScope);

    myAutoMakeTask = new BuildManagerPeriodicTask(coroutineScope) {
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
        final long threshold =
          Registry.intValue("compiler.automake.postpone.when.idle.less.than", 3000); // todo: UI option instead of registry?
        final long idleSinceLastActivity = ApplicationManager.getApplication().getIdleTime();
        return idleSinceLastActivity < threshold;
      }
    };
    myDocumentSaveTask = new BuildManagerPeriodicTask(coroutineScope) {
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

      private static boolean shouldSaveDocuments() {
        final Project contextProject = getCurrentContextProject();
        return contextProject != null && canStartAutoMake(contextProject);
      }
    };

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

    SimpleMessageBusConnection connection = application.getMessageBus().connect(coroutineScope);
    connection.subscribe(ProjectCloseListener.TOPIC, new ProjectWatcher());
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (!IS_UNIT_TEST_MODE) {
          synchronized (myUnprocessedEvents) {
            myUnprocessedEvents.addAll(events);
          }
          myAutomakeTrigger.execute(() -> {
            if (!application.isDisposed()) {
              ReadAction.run(() -> {
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

      private static boolean shouldTriggerMake(List<? extends VFileEvent> events) {
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
            if (ProjectUtil.isProjectOrWorkspaceFile(eventFile) ||
                GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(eventFile, project)) {
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
      RegistryManagerKt.useRegistryManagerWhenReadyJavaAdapter(coroutineScope, registryManager -> {
        configureIdleAutomake(registryManager);
        return Unit.INSTANCE;
      });
    }

    ShutDownTracker.getInstance().registerShutdownTask(() -> {
      stopListening();
      clearWslProxyCache();
    });

    if (!IS_UNIT_TEST_MODE) {
      ScheduledFuture<?> future =
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> runCommand(myGCTask), 3, 180, TimeUnit.MINUTES);
      Disposer.register(this, () -> future.cancel(false));
    }
  }

  private void configureIdleAutomake(@NotNull RegistryManager registryManager) {
    int idleTimeout = getAutomakeWhileIdleTimeout(registryManager);
    int listenerTimeout = idleTimeout > 0 ? idleTimeout : 60000;
    Ref<AccessToken> idleListenerHandle = new Ref<>();
    idleListenerHandle.set(IdleTracker.getInstance().addIdleListener(listenerTimeout, () -> {
      int currentTimeout = getAutomakeWhileIdleTimeout(registryManager);
      if (idleTimeout != currentTimeout) {
        // re-schedule with a changed period
        AccessToken removeListener = idleListenerHandle.get();
        if (removeListener != null) {
          removeListener.close();
        }
        configureIdleAutomake(registryManager);
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
          // only schedule automake if the feature is enabled, no automake is running at the moment,
          // and there is at least one watched project with pending changes
          scheduleAutoMake();
        }
      }
    }));
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

  private static @NotNull List<Project> getOpenProjects() {
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

  public void runCommand(@NotNull Runnable command) {
    myRequestsProcessor.execute(command);
  }

  public sealed interface Changes permits Changes.Incomplete, Changes.Paths {

    static Changes createIncomplete() {
      return Incomplete.instance;
    }

    record Paths(Iterable<InternedPath> deleted, Iterable<InternedPath> changed) implements Changes {
    }

    final class Incomplete implements Changes {
      public static final Incomplete instance = new Incomplete();
    }
  }

  public void notifyChanges(Supplier<? extends Changes> changeProvider) {
    // ensure events are processed in the order they arrived
    runCommand(() -> {
      if (changeProvider.get() instanceof Changes.Paths paths) {
        synchronized (myProjectDataMap) {
          for (Map.Entry<String, ProjectData> entry : myProjectDataMap.entrySet()) {
            ProjectData data = entry.getValue();
            // If a file name differs ony in case, on case-insensitive file systems such name still denotes the same file.
            // In this situation filesDeleted and filesChanged sets will contain paths which are different only in case.
            // Thus, the order in which BuildManager processes the changes, is important:
            // first deleted paths must be processed and only then changed paths
            boolean changed = data.addDeleted(Iterators.filter(paths.deleted(), PATH_FILTER::test));
            changed |= data.addChanged(Iterators.filter(paths.changed(), PATH_FILTER::test));
            if (changed) {
              RequestFuture<?> future = myBuildsInProgress.get(entry.getKey());
              if (future != null && !future.isCancelled() && !future.isDone()) {
                final UUID sessionId = future.getRequestID();
                final Channel channel = myMessageDispatcher.getConnectedChannel(sessionId);
                if (channel != null) {
                  CmdlineRemoteProto.Message.ControllerMessage.FSEvent event = data.createNextEvent(wslPathMapper(entry.getKey()));
                  final CmdlineRemoteProto.Message.ControllerMessage message =
                    CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(
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
        }
      }
      else {
        // incomplete changes
        // force FS rescan on the next build, because information from event may be incomplete
        clearState();
      }
    });
  }

  private static @Nullable Project findProjectByProjectPath(@NotNull String projectPath) {
    return ContainerUtil.find(ProjectManager.getInstance().getOpenProjects(), project -> projectPath.equals(getProjectPath(project)));
  }

  private static @NotNull Function<String, String> wslPathMapper(@Nullable WSLDistribution distribution) {
    return distribution == null ?
           Function.identity() :
           path -> {
             WslPath wslPath = WslPath.parseWindowsUncPath(path);
             return wslPath != null && wslPath.getDistribution().getId().equalsIgnoreCase(distribution.getId())
                    ? wslPath.getLinuxPath()
                    : path;
           };
  }

  private static Function<String, String> wslPathMapper(@NotNull String projectPath) {
    return new Function<>() {
      private Function<String, String> myImpl;

      @Override
      public String apply(String path) {
        if (myImpl != null) {
          return myImpl.apply(path);
        }
        if (WslPath.isWslUncPath(path)) {
          Project project = findProjectByProjectPath(projectPath);
          myImpl = wslPathMapper(project != null ? findWSLDistribution(project) : null);
          return myImpl.apply(path);
        }
        return path;
      }
    };
  }

  public void clearState(@NotNull Project project) {
    String projectPath = getProjectPath(project);
    cancelPreloadedBuilds(projectPath);

    synchronized (myProjectDataMap) {
      ProjectData data = myProjectDataMap.get(projectPath);
      if (data != null) {
        data.dropChanges();
      }
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
        Set<InternedPath> changed = data.myChanged;
        return Iterators.collect(Iterators.map(changed, InternedPath::getValue), new ArrayList<>(changed.size()));
      }
      return null;
    }
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

  private static @NotNull String getThreadTrace(Thread thread, final int depth) { // debugging
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
    final int appIdleTimeout = getAutomakeWhileIdleTimeout(RegistryManager.getInstance());
    if (appIdleTimeout > 0 && ApplicationManager.getApplication().getIdleTime() > appIdleTimeout) {
      // been idle for quite a while, so try to process all open projects while idle
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
        project, false, true, false, scopes, Collections.emptyList(), Collections.singletonMap(BuildParametersKeys.IS_AUTOMAKE, "true"),
        handler
      );
      myAutomakeFutures.put(future, project);
      futures.add(new Pair<>(future, handler));
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

  private static int getAutomakeWhileIdleTimeout(@NotNull RegistryManager registryManager) {
    return registryManager.intValue("compiler.automake.build.while.idle.timeout", 60000);
  }

  private static boolean canStartAutoMake(@NotNull Project project) {
    if (project.isDisposed()) {
      return false;
    }
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    if (!config.MAKE_PROJECT_ON_SAVE || !TrustedProjects.isProjectTrusted(project)) {
      return false;
    }
    return config.allowAutoMakeWhileRunningApplication() || !hasRunningProcess(project);
  }

  private static @Nullable Project getCurrentContextProject() {
    final List<Project> openProjects = getOpenProjects();
    if (openProjects.isEmpty()) {
      return null;
    }
    if (openProjects.size() == 1) {
      return openProjects.get(0);
    }

    Project project = null;
    if (!GraphicsEnvironment.isHeadless()) {
      Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (window == null) {
        window = ComponentUtil.getActiveWindow();
      }
      project = getProjectForComponent(window);
    }

    return isValidProject(project) ? project : null;
  }

  private static boolean hasRunningProcess(@NotNull Project project) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (!handler.isProcessTerminated() && !ALLOW_AUTOMAKE.get(handler, Boolean.FALSE)) { // active process
        return true;
      }
    }
    return false;
  }

  public @NotNull Collection<TaskFuture<?>> cancelAutoMakeTasks(@NotNull Project project) {
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
    String[] paths = ArrayUtil.toStringArray(myPreloadedBuilds.keySet());
    for (String path : paths) {
      cancelPreloadedBuilds(path);
    }
  }

  public @NotNull TaskFuture<Boolean> cancelPreloadedBuilds(@NotNull Project project) {
    return cancelPreloadedBuilds(getProjectPath(project));
  }

  private @NotNull TaskFuture<Boolean> cancelPreloadedBuilds(@NotNull String projectPath) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cancel preloaded build for " + projectPath + "\n" + getThreadTrace(Thread.currentThread(), 50));
    }
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    runCommand(() -> {
      Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> pair = takePreloadedProcess(projectPath);
      if (pair != null) {
        stopProcess(projectPath, pair.first.getRequestID(), pair.second, future);
      }
      else {
        future.complete(Boolean.TRUE);
      }
    });
    return new TaskFutureAdapter<>(future);
  }

  private void stopProcess(@NotNull String projectPath,
                           @NotNull UUID sessionId,
                           @NotNull OSProcessHandler processHandler,
                           @Nullable CompletableFuture<Boolean> future) {
    myMessageDispatcher.cancelSession(sessionId);
    // waiting for the process from project's task queue guarantees no build is started for this project
    // until this one gracefully exits and closes all its storages
    getProjectData(projectPath).taskQueue.execute(() -> {
      Throwable error = null;
      try {
        while (!processHandler.waitFor()) {
          LOG.info("processHandler.waitFor() returned false for session " + sessionId + ", continue waiting");
        }
      }
      catch (Throwable e) {
        error = e;
      }
      finally {
        notifySessionTerminationIfNeeded(sessionId, error);
        if (future != null) {
          future.complete(error == null ? Boolean.TRUE : Boolean.FALSE);
        }
      }
    });
  }

  private @Nullable Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> takePreloadedProcess(String projectPath) {
    Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler> result;
    final Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>> preloadProgress =
      myPreloadedBuilds.remove(projectPath);
    try {
      result = preloadProgress != null ? preloadProgress.get() : null;
    }
    catch (Throwable e) {
      LOG.info(e);
      result = null;
    }
    return result != null && !result.first.isDone() ? result : null;
  }

  public @NotNull TaskFuture<?> scheduleBuild(
    final Project project, final boolean isRebuild, final boolean isMake,
    final boolean onlyCheckUpToDate, final List<TargetTypeBuildScope> scopes,
    final Collection<String> paths,
    final Map<String, String> userData, final DefaultMessageHandler messageHandler
  ) {

    final String projectPath = getProjectPath(project);
    final boolean isAutomake = messageHandler instanceof AutoMakeMessageHandler;
    final EelDescriptor eelDescriptor = EelProviderUtil.getEelDescriptor(project);
    final WSLDistribution wslDistribution = findWSLDistribution(project);

    Function<String, String> pathMapperBack;

    if (canUseEel() && !EelPathUtils.isProjectLocal(project)) {
      pathMapperBack = e -> EelNioBridgeServiceKt.asNioPath(EelPath.parse(e, eelDescriptor)).toString();
    }
    else {
      pathMapperBack = wslDistribution != null ? wslDistribution::getWindowsPath : null;
    }

    final BuilderMessageHandler handler = new NotifyingMessageHandler(project, messageHandler, pathMapperBack, isAutomake);
    Function<String, String> pathMapper;

    if (canUseEel() && !EelPathUtils.isProjectLocal(project)) {
      pathMapper = e -> asEelPath(Path.of(e)).toString();
    }
    else {
      pathMapper = wslDistribution != null ? wslDistribution::getWslPath : Function.identity();
    }

    final DelegateFuture _future = new DelegateFuture();
    // by using the same queue that processes events,
    // we ensure that the build will be aware of all events that have happened before this request
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

      final RequestFuture<? extends BuilderMessageHandler> future =
        usingPreloadedProcess ? preloadedFuture : new RequestFuture<>(handler, sessionId, new CancelBuildSessionAction<>());
      // futures we need to wait for: either just "future" or both "future" and "buildFuture" below
      List<TaskFuture<?>> delegatesToWait = Collections.singletonList(future);

      if (!usingPreloadedProcess && (future.isCancelled() || project.isDisposed())) {
        // in the case of a preloaded process, the process was already running, so the handler will be notified upon process termination
        handler.sessionTerminated(sessionId);
        future.setDone();
      }
      else {
        String optionsPath = PathManager.getOptionsPath();

        if (canUseEel() && !EelPathUtils.isProjectLocal(project)) {
          optionsPath = asEelPath(
            transferLocalContentToRemote(Path.of(optionsPath), new EelPathUtils.TransferTarget.Temporary(eelDescriptor))).toString();
        }
        else {
          optionsPath = pathMapper.apply(optionsPath);
        }

        final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals =
          CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.newBuilder().setGlobalOptionsPath(optionsPath)
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
                     Iterators.collect(Iterators.map(data.myChanged, InternedPath::getValue), new HashSet<>()) +
                     "; DELETED: " +
                     Iterators.collect(Iterators.map(data.myDeleted, InternedPath::getValue), new HashSet<>()));
          }
          needRescan = data.getAndResetRescanFlag();
          currentFSChanges = needRescan ? null : data.createNextEvent(wslPathMapper(wslDistribution));
          if (LOG.isDebugEnabled()) {
            LOG.debug("Sending to starting build, ordinal=" + (currentFSChanges == null ? null : currentFSChanges.getOrdinal()));
          }
          projectTaskQueue = data.taskQueue;
        }

        String mappedProjectPath = pathMapper.apply(projectPath);
        List<String> mappedPaths = ContainerUtil.map(paths, pathMapper::apply);
        final CmdlineRemoteProto.Message.ControllerMessage params;
        if (isRebuild) {
          params = CmdlineProtoUtil.createBuildRequest(mappedProjectPath, scopes, Collections.emptyList(), userData, globals, null, null);
        }
        else if (onlyCheckUpToDate) {
          params = CmdlineProtoUtil.createUpToDateCheckRequest(mappedProjectPath, scopes, mappedPaths, userData, globals, currentFSChanges);
        }
        else {
          params = CmdlineProtoUtil.createBuildRequest(mappedProjectPath, scopes, isMake ? Collections.emptyList() : mappedPaths, userData,
                                                       globals, currentFSChanges,
                                                       isMake ? CompilerCacheConfigurator.getCacheDownloadSettings(project) : null);
        }
        if (!usingPreloadedProcess) {
          myMessageDispatcher.registerBuildMessageHandler(future, params);
        }

        try {
          Future<?> buildFuture = BackgroundTaskUtil.submitTask(projectTaskQueue, ProjectDisposableService.getInstance(project), () -> {
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
                  // If project state was cleared because of roots changed or this is the first compilation after project opening,
                  // ensure the project model is saved on disk, so that automake sees the latest model state.
                  // For ordinary "make all" build, app settings and unsaved docs are always saved before the build starts.
                  try {
                    project.save();
                  }
                  catch (Throwable e) {
                    LOG.info(e);
                  }
                }

                processHandler = launchBuildProcess(project, sessionId, false, wslDistribution, messageHandler.getProgressIndicator());
                errorsOnLaunch = new StringBuffer();
                processHandler.addProcessListener(new StdOutputCollector((StringBuffer)errorsOnLaunch));
                processHandler.startNotify();
              }

              @NlsSafe String debugPort = processHandler.getUserData(COMPILER_PROCESS_DEBUG_HOST_PORT);
              if (debugPort != null) {
                messageHandler.handleCompileMessage(
                  sessionId, CmdlineProtoUtil.createCompileProgressMessageResponse(
                    "Build: waiting for debugger connection on port " + debugPort //NON-NLS
                  ).getCompileMessage());
                // additional support for debugger auto-attach feature
                //noinspection UseOfSystemOutOrSystemErr
                System.out.println("Build: Listening for transport dt_socket at address: " + debugPort); //NON-NLS
              }

              while (!processHandler.waitFor()) {
                LOG.info("processHandler.waitFor() returned false for session " + sessionId + ", continue waiting");
              }

              final int exitValue = processHandler.getProcess().exitValue();
              if (exitValue != 0) {
                final @Nls StringBuilder msg = new StringBuilder();
                msg.append(JavaCompilerBundle.message("abnormal.build.process.termination")).append(": ");
                if (errorsOnLaunch != null && !errorsOnLaunch.isEmpty()) {
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
                      myPreloadedBuilds.put(projectPath, launchPreloadedBuildProcess(project, projectTaskQueue, wslDistribution));
                    }
                    catch (Throwable e) {
                      LOG.info("Error pre-loading build process for project " + projectPath, e);
                    }
                  }
                });
              }
            }
          }).getFuture();
          delegatesToWait = List.of(future, new TaskFutureAdapter<>(buildFuture));
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

  private static boolean canUseEel() {
    return Registry.is("compiler.build.can.use.eel");
  }

  private boolean isProcessPreloadingEnabled(Project project) {
    // automatically disable process preloading when debugging or testing
    if (IS_UNIT_TEST_MODE || !Registry.is("compiler.process.preload") || myBuildProcessDebuggingEnabled) {
      return false;
    }
    if (canUseEel()) {
      Path projectFilePath = Path.of(getProjectPath(project));
      if (!EelProviderUtil.getEelDescriptor(projectFilePath).equals(LocalEelDescriptor.INSTANCE)) {
        // non-local projects do not work correctly with preloaded processes
        return false;
      }
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
      // Either the connection has never been established (a process not started or execution failed),
      // or no messages were sent from the launched process.
      // In this case, the session cannot be unregistered by the message dispatcher
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

  private synchronized int getWslPort(WSLDistribution dist, InetSocketAddress localAddress) {
    return myWslProxyCache
      .computeIfAbsent(dist.getId() + ":" + localAddress, key -> new WslProxy(dist, localAddress))
      .getWslIngressPort();
  }

  private synchronized void cleanWslProxies(WSLDistribution dist) {
    String idPrefix = dist.getId() + ":";
    List<String> keys = new SmartList<>();
    for (String key : myWslProxyCache.keySet()) {
      if (key.startsWith(idPrefix)) {
        keys.add(key);
      }
    }
    for (String key : keys) {
      WslProxy proxy = myWslProxyCache.remove(key);
      if (proxy != null) {
        Disposer.dispose(proxy);
      }
    }
  }

  @Override
  public void dispose() {
    stopListening();
    clearWslProxyCache();
    myAutomakeTrigger.cancel();
    myRequestsProcessor.cancel();
  }

  public static @NotNull Pair<@NotNull Sdk, @Nullable JavaSdkVersion> getBuildProcessRuntimeSdk(@NotNull Project project) {

    return getRuntimeSdk(project, MINIMUM_REQUIRED_JPS_BUILD_JAVA_VERSION, processed -> {
      // build's fallback SDK choosing policy: select among unprocessed SDKs in the SDK table the oldest possible one that can be used
      // select only SDKs that match project's WSL VM, if any
      Supplier<WSLDistribution> projectWslDistribution = new Supplier<>() {
        private WSLDistribution val;

        @Override
        public WSLDistribution get() {
          return val != null ? val : (val = findWSLDistribution(project));
        }
      };
      Predicate<Sdk> sdkFilter = sdk -> !processed.contains(sdk) && Objects.equals(projectWslDistribution.get(), findWSLDistribution(sdk));

      return StreamEx.of(ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()))
        .filter(sdkFilter)
        .mapToEntry(sdk -> JavaVersion.tryParse(sdk.getVersionString()))
        .filterValues(version -> version != null && version.isAtLeast(MINIMUM_REQUIRED_JPS_BUILD_JAVA_VERSION))
        .min(Map.Entry.comparingByValue())
        .map(p -> Pair.create(p.getKey(), JavaSdkVersion.fromJavaVersion(p.getValue())))
        .filter(p -> p.second != null)
        .orElseGet(BuildManager::getIDERuntimeSdk);
    });
  }

  public static @NotNull Pair<@NotNull Sdk, @Nullable JavaSdkVersion> getJavacRuntimeSdk(@NotNull Project project) {
    return getRuntimeSdk(project, ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION, processed -> getIDERuntimeSdk());
  }

  private static @NotNull Pair<Sdk, JavaSdkVersion> getRuntimeSdk(@NotNull Project project,
                                                                  int oldestPossibleVersion,
                                                                  Function<Set<? extends Sdk>, Pair<Sdk, JavaSdkVersion>> fallbackSdkProvider) {
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

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    if (configuration.getJavacCompiler().equals(configuration.getDefaultCompiler()) &&
        JavacConfiguration.getOptions(project, JavacConfiguration.class).PREFER_TARGET_JDK_COMPILER) {
      // for javac, if compiler from associated SDK instead of cross-compilation is preferred, use a different policy:
      // select the most frequently used jdk from the sdks that are associated with the project, but not older than <oldestPossibleVersion>
      // this policy attempts to compile as many modules as possible without spawning a separate javac process

      final List<Pair<Sdk, JavaSdkVersion>> sortedSdks = EntryStream.of(candidates)
        .reverseSorted(Map.Entry.comparingByValue())
        .keys()
        .mapToEntry(sdk -> JavaVersion.tryParse(sdk.getVersionString()))
        .filterValues(version -> version != null && version.isAtLeast(oldestPossibleVersion))
        .mapValues(JavaSdkVersion::fromJavaVersion)
        .nonNullValues()
        .mapKeyValue(Pair::create)
        .toList();

      if (!sortedSdks.isEmpty()) {
        // first, try to find the most used JDK of version 9+ => JRT FS support will be needed
        final Pair<Sdk, JavaSdkVersion> sdk9_plus = ContainerUtil.find(sortedSdks, p -> p.second.isAtLeast(JavaSdkVersion.JDK_1_9));
        return sdk9_plus != null ? sdk9_plus : sortedSdks.iterator().next(); // get the most used
      }
    }

    // now select the latest version from the sdks that are used in the project, but not older than <oldestPossibleVersion>
    return StreamEx.ofKeys(candidates)
      .mapToEntry(sdk -> JavaVersion.tryParse(sdk.getVersionString()))
      .filterValues(version -> version != null && version.isAtLeast(oldestPossibleVersion))
      .max(Map.Entry.comparingByValue())
      .map(p -> Pair.create(p.getKey(), JavaSdkVersion.fromJavaVersion(p.getValue())))
      .filter(p -> p.second != null)
      .orElseGet(() -> fallbackSdkProvider.apply(candidates.keySet()));
  }

  private static @NotNull Pair<Sdk, JavaSdkVersion> getIDERuntimeSdk() {
    @SuppressWarnings("removal")
    Sdk sdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    return Pair.create(sdk, JavaSdk.getInstance().getVersion(sdk));
  }

  private Future<Pair<RequestFuture<PreloadedProcessMessageHandler>, OSProcessHandler>> launchPreloadedBuildProcess(final Project project,
                                                                                                                    ExecutorService projectTaskQueue,
                                                                                                                    @Nullable WSLDistribution projectWslDistribution) {

    // launching the build process from projectTaskQueue ensures that no other build process for this project is currently running
    return BackgroundTaskUtil.submitTask(projectTaskQueue, ProjectDisposableService.getInstance(project), () -> {
      if (project.isDisposed()) {
        return null;
      }
      final RequestFuture<PreloadedProcessMessageHandler> future =
        new RequestFuture<>(new PreloadedProcessMessageHandler(), UUID.randomUUID(),
                            new CancelBuildSessionAction<>());
      try {
        myMessageDispatcher.registerBuildMessageHandler(future, null);
        ProgressIndicator indicator = Objects.requireNonNull(ProgressManager.getGlobalProgressIndicator());
        final OSProcessHandler processHandler = launchBuildProcess(project, future.getRequestID(), true, projectWslDistribution, indicator);
        final StringBuffer errors = new StringBuffer();
        processHandler.addProcessListener(new StdOutputCollector(errors));
        STDERR_OUTPUT.set(processHandler, errors);

        processHandler.startNotify();
        return new Pair<>(future, processHandler);
      }
      catch (Throwable e) {
        handleProcessExecutionFailure(future.getRequestID(), e);
        ExceptionUtil.rethrowUnchecked(e);
        throw new RuntimeException(e);
      }
    }).getFuture();
  }

  private static @Nullable WSLDistribution findWSLDistribution(@NotNull Project project) {
    return findWSLDistribution(ProjectRootManager.getInstance(project).getProjectSdk());
  }

  private static @Nullable WSLDistribution findWSLDistribution(@Nullable Sdk sdk) {
    if (sdk != null && sdk.getSdkType() instanceof JavaSdkType projectJdkType) {
      String windowsUncPath = projectJdkType.getVMExecutablePath(sdk);
      if (windowsUncPath != null) {
        return WslPath.getDistributionByWindowsUncPath(windowsUncPath);
      }
    }
    return null;
  }

  private OSProcessHandler launchBuildProcess(@NotNull Project project,
                                              @NotNull UUID sessionId,
                                              boolean requestProjectPreload,
                                              @Nullable WSLDistribution projectWslDistribution,
                                              @Nullable ProgressIndicator progressIndicator) throws ExecutionException {
    String compilerPath = null;
    final String vmExecutablePath;
    JavaSdkVersion sdkVersion = null;

    final String forcedCompiledJdkHome = Registry.stringValue(COMPILER_PROCESS_JDK_PROPERTY);
    String sdkName;
    if (Strings.isEmptyOrSpaces(forcedCompiledJdkHome)) {
      // choosing sdk with which the build process should be run
      final Pair<Sdk, JavaSdkVersion> pair = getBuildProcessRuntimeSdk(project);
      final Sdk projectJdk = pair.first;
      sdkName = projectJdk.getName();
      sdkVersion = pair.second;

      final JavaSdkType projectJdkType = (JavaSdkType)projectJdk.getSdkType();

      // validate tools.jar presence for jdk8 and older
      if (!JavaSdkUtil.isJdkAtLeast(projectJdk, JavaSdkVersion.JDK_1_9)) {
        @SuppressWarnings("removal") final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
        if (FileUtil.pathsEqual(projectJdk.getHomePath(), internalJdk.getHomePath())) {
          // important: because internal JDK can be either JDK or JRE,
          // this is the most universal way to obtain tools.jar path in this particular case
          final JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
          if (systemCompiler == null) {
            //temporary workaround for IDEA-169747
            try {
              compilerPath = ClasspathBootstrap.getResourcePath(
                Class.forName("com.sun.tools.javac.api.JavacTool", false, BuildManager.class.getClassLoader()));
            }
            catch (Throwable t) {
              LOG.info(t);
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
            throw new ExecutionException(
              JavaCompilerBundle.message("build.process.no.javac.path.found", sdkName, projectJdk.getHomePath()));
          }
        }
      }

      vmExecutablePath = projectJdkType.getVMExecutablePath(projectJdk);
      project.getService(BuildManagerVersionChecker.class).checkArch(projectJdk.getHomePath());
    }
    else {
      sdkName = forcedCompiledJdkHome;
      compilerPath = new File(forcedCompiledJdkHome, "lib/tools.jar").getAbsolutePath();
      vmExecutablePath = new File(forcedCompiledJdkHome, "bin/java").getAbsolutePath();
      project.getService(BuildManagerVersionChecker.class).checkArch(forcedCompiledJdkHome);
    }

    final CompilerConfiguration projectConfig = CompilerConfiguration.getInstance(project);
    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);

    InetAddress listenAddress = InetAddress.getLoopbackAddress();
    InetSocketAddress listenSocketAddress = new InetSocketAddress(listenAddress, ensureListening(listenAddress));
    String buildProcessConnectHost = listenAddress.getHostAddress();
    int buildProcessConnectPort = listenSocketAddress.getPort();

    BuildCommandLineBuilder cmdLine;
    WslPath wslPath;
    if (canUseEel() && !EelPathUtils.isProjectLocal(project)) {
      wslPath = null;
      EelBuildCommandLineBuilder eelBuilder = new EelBuildCommandLineBuilder(project, Path.of(vmExecutablePath));
      cmdLine = eelBuilder;
      cmdLine.addParameter("-Dide.jps.remote.path.prefixes=" + eelBuilder.pathPrefixes().stream()
        .map(e -> e.replace('\\', '/')).collect(Collectors.joining(";")));
      buildProcessConnectHost = "127.0.0.1";
      int listenPort = listenSocketAddress.getPort();
      buildProcessConnectPort =
        eelBuilder.maybeRunReverseTunnel(listenPort, project); // TODO maybeRunReverseTunnel must return InetSocketAddress
    }
    else {
      wslPath = WslPath.parseWindowsUncPath(vmExecutablePath);
      if (wslPath != null) {
        WSLDistribution sdkDistribution = wslPath.getDistribution();
        if (!sdkDistribution.equals(projectWslDistribution)) {
          throw new ExecutionException(JavaCompilerBundle.message("build.process.wsl.distribution.dont.match",
                                                                  sdkName + " (WSL " + sdkDistribution.getPresentableName() + ")",
                                                                  MINIMUM_REQUIRED_JPS_BUILD_JAVA_VERSION));
        }
        cmdLine = new WslBuildCommandLineBuilder(project, sdkDistribution, wslPath.getLinuxPath(), progressIndicator);
        buildProcessConnectHost = "127.0.0.1"; // WslProxy listen address on linux side
        buildProcessConnectPort = getWslPort(sdkDistribution, listenSocketAddress);
      }
      else {
        if (projectWslDistribution != null) {
          throw new ExecutionException(
            JavaCompilerBundle.message("build.process.wsl.distribution.dont.match", sdkName, MINIMUM_REQUIRED_JPS_BUILD_JAVA_VERSION));
        }
        cmdLine = new LocalBuildCommandLineBuilder(vmExecutablePath);
      }
    }

    boolean profileWithYourKit = false;
    boolean isAgentpathSet = false;
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
          //noinspection SpellCheckingInspection
          if (option.startsWith("-Dprofiling.mode=") && !option.equals("-Dprofiling.mode=false")) {
            profileWithYourKit = true;
          }
          if (option.startsWith("-agentpath:")) {
            isAgentpathSet = true;
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
      final int heapSize =
        projectConfig.getBuildProcessHeapSize(JavacConfiguration.getOptions(project, JavacConfiguration.class).MAXIMUM_HEAP_SIZE);
      cmdLine.addParameter("-Xmx" + heapSize + "m");
    }

    cmdLine.addParameter("-Djava.awt.headless=true");
    if (Boolean.getBoolean(JPS_USE_EXPERIMENTAL_STORAGE)) {
      cmdLine.addParameter("-D" + JPS_USE_EXPERIMENTAL_STORAGE + "=true");
    }

    attachJnaBootLibraryIfNeeded(project, cmdLine, wslPath);
    if (Registry.is("jps.build.use.workspace.model")) {
      // todo: upload workspace model to remote side because it runs with eel
      String globalCacheId = "Local";

      cmdLine.addParameter("-Dintellij.jps.use.workspace.model=true");
      WorkspaceModelCache cache = WorkspaceModelCache.getInstance(project);
      GlobalWorkspaceModelCache globalCache = GlobalWorkspaceModelCache.Companion.getInstance();
      if (cache != null && globalCache != null) {
        //todo ensure that caches are up-to-date or use a different way to pass serialized workspace model to the build process
        cmdLine.addParameter("-Djps.workspace.storage.project.cache.path=" + cache.getCacheFile());
        cmdLine.addParameter(
          "-Djps.workspace.storage.global.cache.path=" + globalCache.cacheFile(new InternalEnvironmentNameImpl(globalCacheId)));
        cmdLine.addParameter(
          "-Djps.workspace.storage.relative.paths.in.cache=" + Registry.is("ide.workspace.model.store.relative.paths.in.cache", false));
      }
      else {
        LOG.info("Workspace model caches aren't available and won't be used in the build process");
      }
      cmdLine.addParameter(
        "--add-opens=java.base/java.util=ALL-UNNAMED");//used by com.esotericsoftware.kryo.kryo5.serializers.CachedFields.addField
    }

    if (sdkVersion != null) {
      if (sdkVersion.compareTo(JavaSdkVersion.JDK_1_9) < 0) {
        //-Djava.endorsed.dirs is not supported in JDK 9+, may result in abnormal process termination
        cmdLine.addParameter("-Djava.endorsed.dirs=\"\""); // turn off all jre customizations for predictable behaviour
      }
      if (sdkVersion.isAtLeast(JavaSdkVersion.JDK_16)) {
        // enable javac-related reflection tricks in JPS
        ClasspathBootstrap.configureReflectionOpenPackages(cmdLine::addParameter);
      }
    }
    if (IS_UNIT_TEST_MODE) {
      //noinspection SpellCheckingInspection
      cmdLine.addParameter("-Dtest.mode=true");
    }

    if (requestProjectPreload) {
      //noinspection SpellCheckingInspection
      cmdLine.addPathParameter("-Dpreload.project.path=", FileUtil.toCanonicalPath(getProjectPath(project)));
      //noinspection SpellCheckingInspection
      cmdLine.addPathParameter("-Dpreload.config.path=", FileUtil.toCanonicalPath(PathManager.getOptionsPath()));
    }

    if (ProjectUtilCore.isExternalStorageEnabled(project)) {
      Path externalProjectConfig = ProjectUtil.getExternalConfigurationDir(project);
      if (canUseEel() && !EelPathUtils.isProjectLocal(project)) {
        try {
          cmdLine.addPathParameter(
            "-D" + GlobalOptions.EXTERNAL_PROJECT_CONFIG + '=',
            cmdLine.copyProjectSpecificPathToTargetIfRequired(project, externalProjectConfig)
          );
        }
        catch (NoSuchFileException ignored) {
          // No external project cache -- no copy of external project cache.
        }
        catch (FileSystemException err) {
          throw new ExecutionException(
            JavaCompilerBundle.message("build.manager.launch.build.process.failed.to.copy.external.project.configuration"), err);
        }
      }
      else {
        String pathToExternalStorage = externalProjectConfig.toString();
        cmdLine.addPathParameter("-D" + GlobalOptions.EXTERNAL_PROJECT_CONFIG + '=', pathToExternalStorage);
      }
    }

    cmdLine.addParameter("-D" + GlobalOptions.COMPILE_PARALLEL_OPTION + '=' + projectConfig.isParallelCompilationEnabled());
    if (projectConfig.isParallelCompilationEnabled()) {
      if (!Registry.is("compiler.automake.allow.parallel", true)) {
        cmdLine.addParameter("-D" + GlobalOptions.ALLOW_PARALLEL_AUTOMAKE_OPTION + "=false");
      }
    }
    cmdLine.addParameter("-D" + GlobalOptions.REBUILD_ON_DEPENDENCY_CHANGE_OPTION + '=' + config.REBUILD_ON_DEPENDENCY_CHANGE);
    cmdLine.addParameter("-Didea.IntToIntBtree.page.size=32768");

    if (Registry.is("compiler.build.report.statistics")) {
      cmdLine.addParameter("-D" + GlobalOptions.REPORT_BUILD_STATISTICS + "=true");
    }
    if (Registry.is("compiler.natural.int.multimap.impl")) {  // todo: temporary flag to evaluate experimental multi-map implementation
      //noinspection SpellCheckingInspection
      cmdLine.addParameter("-Djps.mappings.natural.int.multimap.impl=true");
    }

    // third-party library tweaks
    //noinspection SpellCheckingInspection
    cmdLine.addParameter("-Djdt.compiler.useSingleThread=true"); // always run Eclipse compiler in single-threaded mode
    //noinspection SpellCheckingInspection
    cmdLine.addParameter("-Daether.connector.resumeDownloads=false"); // always re-download maven libraries if partially downloaded
    cmdLine.addParameter("-Dio.netty.initialSeedUniquifier=" +
                         ThreadLocalRandom.getInitialSeedUniquifier()); // this will make netty initialization faster on some systems

    for (String option : userAdditionalOptionsList) {
      cmdLine.addParameter(option);
    }

    cmdLine.setupAdditionalVMOptions();

    Path hostWorkingDirectory = cmdLine.getHostWorkingDirectory();
    if (!FileUtil.createDirectory(hostWorkingDirectory.toFile())) {
      LOG.warn("Failed to create build working directory " + hostWorkingDirectory);
    }

    if (profileWithYourKit) {
      YourKitProfilerService service = ApplicationManager.getApplication().getService(YourKitProfilerService.class);
      if (service != null) {
        try {
          service.copyYKLibraries(hostWorkingDirectory);
        }
        catch (IOException e) {
          LOG.warn("Failed to copy YK libraries", e);
        }
        @SuppressWarnings("SpellCheckingInspection") final StringBuilder parameters =
          new StringBuilder().append("-agentpath:").append(cmdLine.getYjpAgentPath(service))
            .append("=disablealloc,delay=10000,sessionname=ExternalBuild");
        final String buildSnapshotPath = System.getProperty("build.snapshots.path");
        if (buildSnapshotPath != null) {
          parameters.append(",dir=").append(buildSnapshotPath);
        }
        cmdLine.addParameter(parameters.toString());
        showSnapshotNotificationAfterFinish(project);
      }
      else {
        LOG.warn("Performance Plugin is missing or disabled; skipping YJP agent configuration");
        if (isAgentpathSet) {
          showSnapshotNotificationAfterFinish(project);
        }
      }
    }

    // debugging
    String debugPort = null;
    if (myBuildProcessDebuggingEnabled) {
      debugPort = StringUtil.nullize(Registry.stringValue("compiler.process.debug.port"));
      if (debugPort == null) {
        try {
          debugPort = String.valueOf(NetUtils.findAvailableSocketPort());
        }
        catch (IOException e) {
          throw new ExecutionException(JavaCompilerBundle.message("build.process.no.free.debug.port"), e);
        }
      }
      cmdLine.addParameter("-XX:+HeapDumpOnOutOfMemoryError");
      // Both formats "<host>:<port>" and "<port>" are accepted.
      // https://docs.oracle.com/en/java/javase/11/docs/specs/jpda/conninv.html#socket-transport
      cmdLine.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
    }

    // portable caches
    if (RegistryManager.getInstance().is("compiler.process.use.portable.caches") &&
        CompilerCacheConfigurator.isServerUrlConfigured(project) &&
        CompilerCacheStartupActivity.isLineEndingsConfiguredCorrectly()) {
      cmdLine.addParameter("-D" + ProjectStamps.PORTABLE_CACHES_PROPERTY + "=true");
    }

    // DepGraph-based IC implementation
    if (AdvancedSettings.getBoolean("compiler.unified.ic.implementation")) {
      cmdLine.addParameter("-D" + GlobalOptions.DEPENDENCY_GRAPH_ENABLED + "=true");
    }

    if (Boolean.parseBoolean(System.getProperty(GlobalOptions.TRACK_LIBRARY_DEPENDENCIES_ENABLED, "false"))) {
      cmdLine.addParameter("-D" + GlobalOptions.TRACK_LIBRARY_DEPENDENCIES_ENABLED + "=true");
    }

    // Java compiler's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    cmdLine.setCharset(mySystemCharset);
    cmdLine.addParameter("-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + '=' + mySystemCharset.name());
    for (String name : INHERITED_IDE_VM_OPTIONS) {
      final String value = System.getProperty(name);
      if (value != null) {
        cmdLine.addParameter("-D" + name + '=' + value);
      }
    }
    for (String name : Set.of("intellij.test.jars.location", "intellij.test.jars.mapping.file")) {
      final String value = System.getProperty(name);
      if (value != null) {
        cmdLine.addPathParameter("-D" + name + "=", value);
      }
    }
    final DynamicBundle.LanguageBundleEP languageBundle = LocalizationUtil.INSTANCE.findLanguageBundle();
    if (languageBundle != null) {
      final PluginDescriptor pluginDescriptor = languageBundle.pluginDescriptor;
      final ClassLoader loader = pluginDescriptor == null ? null : pluginDescriptor.getClassLoader();
      final String bundlePath = loader == null ? null : PathManager.getResourceRoot(loader, "META-INF/plugin.xml");
      if (bundlePath != null) {
        cmdLine.addParameter("-D" + GlobalOptions.LANGUAGE_BUNDLE + '=' + FileUtil.toSystemIndependentName(bundlePath));
      }
    }
    if (canUseEel() && EelPathUtils.isProjectLocal(project)) {
      cmdLine.addPathParameter("-D" + PathManager.PROPERTY_HOME_PATH + '=', FileUtil.toSystemIndependentName(PathManager.getHomePath()));
      cmdLine.addPathParameter("-D" + PathManager.PROPERTY_CONFIG_PATH + '=',
                               FileUtil.toSystemIndependentName(PathManager.getConfigPath()));
      cmdLine.addPathParameter("-D" + PathManager.PROPERTY_PLUGINS_PATH + '=',
                               FileUtil.toSystemIndependentName(PathManager.getPluginsPath()));
    }

    Path logPath = Path.of(FileUtil.toSystemIndependentName(getBuildLogDirectory().getAbsolutePath()));
    cmdLine.addPathParameter("-D" + GlobalOptions.LOG_DIR_OPTION + '=', logPath);
    if (AdvancedSettings.getBoolean(IN_MEMORY_LOGGER_ADVANCED_SETTINGS_NAME)) {
      cmdLine.addParameter("-D" + GlobalOptions.USE_IN_MEMORY_FAILED_BUILD_LOGGER + "=true");
    }

    if (Registry.is("jps.report.registered.unexistent.output", false)) {
      cmdLine.addParameter("-Djps.report.registered.unexistent.output=true");
    }

    if (myFallbackSdkHome != null && myFallbackSdkVersion != null) {
      cmdLine.addPathParameter("-D" + GlobalOptions.FALLBACK_JDK_HOME + '=', myFallbackSdkHome);
      cmdLine.addParameter("-D" + GlobalOptions.FALLBACK_JDK_VERSION + '=' + myFallbackSdkVersion);
    }
    cmdLine.addParameter("-Dio.netty.noUnsafe=true");


    final File projectSystemRoot = getProjectSystemDirectory(project);
    File projectTempDir = new File(projectSystemRoot.getPath(), TEMP_DIR_NAME);
    projectTempDir.mkdirs();
    cmdLine.addPathParameter("-Djava.io.tmpdir=", FileUtil.toSystemIndependentName(projectTempDir.getPath()));

    for (BuildProcessParametersProvider provider : BuildProcessParametersProvider.EP_NAME.getExtensions(project)) {
      for (String arg : provider.getVMArguments()) {
        cmdLine.addParameter(arg);
      }

      for (Pair<String, Path> parameter : provider.getPathParameters()) {
        try {
          cmdLine.addPathParameter(parameter.getFirst(), cmdLine.copyProjectSpecificPathToTargetIfRequired(project, parameter.getSecond()));
        }
        catch (FileSystemException err) {
          throw new ExecutionException(
            JavaCompilerBundle.message("build.manager.launch.build.process.failed.to.copy.parameter", parameter.getFirst()), err);
        }
      }
    }

    //noinspection SpellCheckingInspection
    cmdLine.addParameter("-Dide.propagate.context=false");
    cmdLine.addParameter("-Dintellij.platform.log.sync=true");

    @SuppressWarnings("UnnecessaryFullyQualifiedName") final Class<?> launcherClass = org.jetbrains.jps.cmdline.Launcher.class;

    final List<String> launcherCp = new ArrayList<>();
    launcherCp.add(ClasspathBootstrap.getResourcePath(launcherClass));
    launcherCp.addAll(BuildProcessClasspathManager.getLauncherClasspath(project));
    if (compilerPath != null) {   // can be null (in the case of JDK 9+)
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
          throw new ExecutionException(
            JavaCompilerBundle.message("build.process.ecj.path.does.not.exist", customEcjPath.getAbsolutePath()));
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
    if (profileWithYourKit) {
      String yjpControllerFileName = "yjp-controller-api-redist.jar";
      Path yjpControllerPath = Path.of(cmdLine.getWorkingDirectory(), yjpControllerFileName);
      if (LOG.isDebugEnabled()) {
        LOG.debug("JPS process profiling with YourKit is enabled, " +
                  "adding '" + yjpControllerFileName + "' to classpath, " +
                  "full path '" + yjpControllerPath + "'");
      }
      if (!Files.exists(yjpControllerPath)) {
        LOG.warn("JPS process profiling is enabled, but '" + yjpControllerPath + "' is missing");
      }

      cmdLine.addClasspathParameter(cp, Collections.singletonList(yjpControllerFileName));
    }
    else {
      cmdLine.addClasspathParameter(cp, Collections.emptyList());
    }

    for (BuildProcessParametersProvider buildProcessParametersProvider : BuildProcessParametersProvider.EP_NAME.getExtensions(project)) {
      for (String path : buildProcessParametersProvider.getAdditionalPluginPaths()) {
        try {
          cmdLine.copyProjectSpecificPathToTargetIfRequired(project, Paths.get(path));
        }
        catch (FileSystemException err) {
          throw new ExecutionException(JavaCompilerBundle.message("build.manager.launch.build.process.failed.to.copy.additional.plugin"),
                                       err);
        }
      }
    }

    cmdLine.addParameter(BuildMain.class.getName());
    cmdLine.addParameter(buildProcessConnectHost);
    cmdLine.addParameter(Integer.toString(buildProcessConnectPort));
    cmdLine.addParameter(sessionId.toString());

    cmdLine.addParameter(cmdLine.getWorkingDirectory());

    boolean lowPriority = AdvancedSettings.getBoolean("compiler.lower.process.priority");
    if (SystemInfo.isUnix && lowPriority) {
      cmdLine.setUnixProcessPriority(10);
    }
    if (SystemInfo.isLinux && Registry.is("compiler.process.new.session", true)) {
      cmdLine.setStartNewSession();
    }

    try {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC)
        .beforeBuildProcessStarted(project, sessionId);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    final OSProcessHandler processHandler = new OSProcessHandler(cmdLine.buildCommandLine()) {
      @Override
      protected boolean shouldDestroyProcessRecursively() {
        return true;
      }

      @Override
      protected @NotNull BaseOutputReader.Options readerOptions() {
        return BaseOutputReader.Options.BLOCKING;
      }
    };
    processHandler.addProcessListener(new ProcessListener() {
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
    if (debugPort != null) {
      processHandler.putUserData(COMPILER_PROCESS_DEBUG_HOST_PORT, debugPort);
    }

    if (SystemInfo.isWindows && lowPriority) {
      try {
        WinProcess winProcess = new WinProcess((int)processHandler.getProcess().pid());
        winProcess.setPriority(Priority.IDLE);
      }
      catch (UnsupportedOperationException ignored) {
        // Process.pid may throw this error. Just ignoring it.
      }
      catch (Throwable e) {
        LOG.error("Cannot set priority", e);
      }
    }

    return processHandler;
  }

  private static void attachJnaBootLibraryIfNeeded(
    @NotNull Project project,
    @NotNull BuildCommandLineBuilder cmdLine,
    @Nullable WslPath wslPath
  ) {
    // it's impossible to use a Windows DLL inside a WSL environment
    if (wslPath != null) {
      return;
    }
    // it's impossible to use a Windows DLL inside a non-local environment
    if (!(EelProviderUtil.getEelDescriptor(project) instanceof LocalEelDescriptor)) {
      return;
    }
    String jnaBootLibraryPath = System.getProperty("jna.boot.library.path");
    if (jnaBootLibraryPath != null) {
      //noinspection SpellCheckingInspection
      cmdLine.addPathParameter("-Djna.boot.library.path=", jnaBootLibraryPath);
      //noinspection SpellCheckingInspection
      cmdLine.addParameter("-Djna.nosys=true");
      //noinspection SpellCheckingInspection
      cmdLine.addParameter("-Djna.noclasspath=true");
    }
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
            RevealFileAction.openDirectory(new File(SystemProperties.getUserHome(), "Snapshots"));
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

  public @NotNull Path getBuildSystemDirectory(Project project) {
    final WslPath wslPath = WslPath.parseWindowsUncPath(getProjectPath(project));
    if (wslPath != null) {
      Path buildDir = WslBuildCommandLineBuilder.getWslBuildSystemDirectory(wslPath.getDistribution());
      if (buildDir != null) {
        return buildDir;
      }
    }
    return LocalBuildCommandLineBuilder.getLocalBuildSystemDirectory();
  }

  public static @NotNull File getBuildLogDirectory() {
    return new File(PathManager.getLogPath(), "build-log");
  }

  public @NotNull Path getProjectSystemDir(@NotNull Project project) {
    return getProjectSystemDirectory(project).toPath();
  }

  public @NotNull File getProjectSystemDirectory(@NotNull Project project) {
    String projectPath = getProjectPath(project);
    WslPath wslPath = WslPath.parseWindowsUncPath(projectPath);
    Function<String, Integer> hashFunction;
    if (wslPath == null) {
      hashFunction = String::hashCode;
    }
    else {
      hashFunction = s -> Objects.requireNonNullElse(wslPath.getDistribution().getWslPath(s), s).hashCode();
    }
    return Utils.getDataStorageRoot(getBuildSystemDirectory(project).toFile(), projectPath, hashFunction);
  }

  private static File getUsageFile(@NotNull File projectSystemDir) {
    //noinspection SpellCheckingInspection
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

  private static @Nullable Pair<Date, File> readUsageFile(File usageFile) {
    try {
      List<String> lines = FileUtil.loadLines(usageFile, StandardCharsets.UTF_8.name());
      if (!lines.isEmpty()) {
        final String dateString = lines.get(0);
        final Date date;
        synchronized (USAGE_STAMP_DATE_FORMAT) {
          date = USAGE_STAMP_DATE_FORMAT.parse(dateString);
        }
        final File projectFile = lines.size() > 1 ? new File(lines.get(1)) : null;
        return Pair.create(date, projectFile);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    return null;
  }

  private synchronized void stopListening() {
    for (ListeningConnection connection : myListeningConnections) {
      connection.myChannelRegistrar.close();
    }
    myListeningConnections.clear();
  }

  private synchronized void clearWslProxyCache() {
    for (WslProxy proxy : myWslProxyCache.values()) {
      try {
        Disposer.dispose(proxy);
      }
      catch (Throwable ignored) {
      }
    }
    myWslProxyCache.clear();
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
    private final Alarm alarm;
    private final AtomicBoolean myInProgress = new AtomicBoolean(false);
    private final Runnable taskRunnable = () -> {
      try {
        runTask();
      }
      finally {
        myInProgress.set(false);
      }
    };

    private final @NotNull CoroutineScope coroutineScope;

    BuildManagerPeriodicTask(@NotNull CoroutineScope coroutineScope) {
      this.coroutineScope = coroutineScope;
      alarm = new Alarm(coroutineScope, Alarm.ThreadToUse.POOLED_THREAD);
    }

    final void schedule() {
      schedule(false);
    }

    private void schedule(boolean increasedDelay) {
      cancelPendingExecution();
      alarm.addRequest(this, Math.max(100, increasedDelay ? 10 * getDelay() : getDelay()));
    }

    void cancelPendingExecution() {
      alarm.cancelAllRequests();
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
          AppJavaExecutorUtil.executeOnPooledIoThread(coroutineScope, taskRunnable);
        }
        catch (CancellationException ignored) {
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
    private final @Nullable Function<String, String> myPathMapper;
    private final boolean myIsAutomake;

    NotifyingMessageHandler(@NotNull Project project,
                            @NotNull BuilderMessageHandler delegateHandler,
                            @Nullable Function<String, String> pathMapper,
                            final boolean isAutomake) {
      myProject = project;
      myDelegateHandler = delegateHandler;
      myPathMapper = pathMapper;
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
          ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC)
            .buildFinished(myProject, sessionId, myIsAutomake);
        }
        catch (ProcessCanceledException ignored) {
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    @Override
    public void handleBuildMessage(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage msg) {
      CmdlineRemoteProto.Message.BuilderMessage _message = msg;
      if (myPathMapper != null) {
        if (_message.hasCompileMessage()) {
          final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage compileMessage = _message.getCompileMessage();
          if (compileMessage.hasSourceFilePath()) {
            final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Builder builder =
              CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.newBuilder(compileMessage);
            builder.setSourceFilePath(myPathMapper.apply(compileMessage.getSourceFilePath()));
            _message = CmdlineRemoteProto.Message.BuilderMessage.newBuilder(_message).setCompileMessage(builder).build();
          }
        }
        if (_message.hasBuildEvent()) {
          final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent buildEvent = _message.getBuildEvent();
          final int filesCount = buildEvent.getGeneratedFilesCount();
          if (filesCount > 0) {
            final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Builder builder =
              CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.newBuilder(buildEvent);
            for (int idx = 0; idx < filesCount; idx++) {
              final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile file = buildEvent.getGeneratedFiles(idx);
              builder.setGeneratedFiles(idx, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile.newBuilder(file)
                .setOutputRoot(myPathMapper.apply(file.getOutputRoot())));
            }
            _message = CmdlineRemoteProto.Message.BuilderMessage.newBuilder(_message).setBuildEvent(builder).build();
          }
        }
      }
      super.handleBuildMessage(channel, sessionId, _message);
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

  @Service(Service.Level.PROJECT)
  private static final class ProjectDisposableService implements Disposable {
    static @NotNull ProjectDisposableService getInstance(@NotNull Project project) {
      return project.getService(ProjectDisposableService.class);
    }

    @Override
    public void dispose() {
      // Nothing.
    }
  }

  static final class BuildManagerStartupActivity implements StartupActivity, DumbAware {
    BuildManagerStartupActivity() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        throw ExtensionNotApplicableException.create();
      }
    }

    @Override
    public void runActivity(@NotNull Project project) {
      MessageBusConnection connection = project.getMessageBus().connect();
      connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
        @Override
        public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
          getInstance().cancelAutoMakeTasks(env.getProject()); // make sure to cancel all automake requests waiting in the build queue
        }

        @Override
        public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
          // make sure to cancel all automake requests added to the build queue after processStaring and before this event
          getInstance().cancelAutoMakeTasks(env.getProject());
        }

        @Override
        public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
          // augmenting reaction to `processTerminated()`: in case any automake requests were canceled before the process start
          getInstance().scheduleAutoMake();
        }

        @Override
        public void processTerminated(@NotNull String executorId,
                                      @NotNull ExecutionEnvironment env,
                                      @NotNull ProcessHandler handler,
                                      int exitCode) {
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

          if (candidates.isEmpty() && !compileContext.isAnnotationProcessorsEnabled()) {
            return;
          }

          BackgroundTaskUtil.submitTask(ProjectDisposableService.getInstance(project), () -> {
            if (project.isDisposed()) {
              return;
            }

            if (compileContext.isAnnotationProcessorsEnabled()) {
              // annotation processors may have re-generated code
              final CompilerConfiguration config = CompilerConfiguration.getInstance(project);
              try {
                for (Module module : ReadAction.nonBlocking(() -> compileContext.getCompileScope().getAffectedModules())
                  .executeSynchronously()) {
                  if (project.isDisposed()) {
                    return;
                  }
                  if (module.isDisposed()) {
                    continue;
                  }
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
              catch (ProcessCanceledException ignored) {
              }
              catch (Throwable e) {
                LOG.info(e);
              }
            }

            if (candidates.isEmpty()) {
              return;
            }

            CompilerUtil.refreshOutputRoots(candidates);

            LocalFileSystem lfs = LocalFileSystem.getInstance();
            try {
              Collection<VirtualFile> toRefresh = ReadAction.nonBlocking(() -> {
                if (project.isDisposed()) {
                  return Collections.<VirtualFile>emptySet();
                }
                ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                return candidates.stream().map(lfs::findFileByPath).filter(root -> root != null && fileIndex.isInSourceContent(root))
                  .collect(Collectors.toSet());
              }).executeSynchronously();

              if (!toRefresh.isEmpty()) {
                lfs.refreshFiles(toRefresh, true, true, null);
              }
            }
            catch (ProcessCanceledException ignored) {
            }
            catch (Throwable e) {
              LOG.info(e);
            }
          });
        }

        @Override
        public void fileGenerated(@NotNull String outputRoot, @NotNull String relativePath) {
          synchronized (myRootsToRefresh) {
            myRootsToRefresh.add(outputRoot);
          }
        }
      });

      String projectPath = getProjectPath(project);
      Disposer.register(ProjectDisposableService.getInstance(project), () -> {
        BuildManager buildManager = getInstance();
        buildManager.cancelPreloadedBuilds(projectPath);
        buildManager.myProjectDataMap.remove(projectPath);
      });

      BuildProcessParametersProvider.EP_NAME.addChangeListener(project, () -> getInstance().cancelAllPreloadedBuilds(), null);

      getInstance().runCommand(() -> {
        try {
          BackgroundTaskUtil
            .submitTask(ProjectDisposableService.getInstance(project), () -> {
              updateUsageFile(project, getInstance().getProjectSystemDirectory(project));
            })
            .awaitCompletion();
        }
        catch (java.util.concurrent.ExecutionException e) {
          ExceptionUtil.rethrowAllAsUnchecked(e.getCause());
        }
      });
      // run automake after a project opened
      getInstance().scheduleAutoMake();
    }
  }

  private final class ProjectWatcher implements ProjectCloseListener {
    @Override
    public void projectClosingBeforeSave(@NotNull Project project) {
      myAutoMakeTask.cancelPendingExecution();
      cancelAutoMakeTasks(project);
    }

    @Override
    public void projectClosing(@NotNull Project project) {
      final String projectPath = getProjectPath(project);
      ProgressManager.getInstance()
        .run(new Task.Modal(project, JavaCompilerBundle.message("progress.title.cancelling.running.builds"), false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            final TaskFuture<?> currentBuild = myBuildsInProgress.get(projectPath);
            if (currentBuild != null) {
              currentBuild.cancel(false);
            }
            myAutoMakeTask.cancelPendingExecution();
            cancelPreloadedBuilds(projectPath);
            for (TaskFuture<?> future : cancelAutoMakeTasks(project)) {
              future.waitFor(500, TimeUnit.MILLISECONDS);
            }
            if (currentBuild != null) {
              currentBuild.waitFor(15, TimeUnit.SECONDS);
            }
          }
        });
      WSLDistribution wslDistr = findWSLDistribution(project);
      if (wslDistr != null) {
        cleanWslProxies(wslDistr);
      }
    }

    @Override
    public void projectClosed(@NotNull Project project) {
      if (myProjectDataMap.remove(getProjectPath(project)) != null) {
        if (myProjectDataMap.isEmpty()) {
          InternedPath.clearCache();
        }
      }
    }
  }

  private static final class ProjectData {
    final @NotNull ExecutorService taskQueue;
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

    boolean addChanged(Iterable<InternedPath> paths) {
      boolean changes = false;
      if (!myNeedRescan) {
        for (InternedPath path : paths) {
          changes |= myDeleted.remove(path);
          changes |= myChanged.add(path);
        }
      }
      return changes;
    }

    boolean addDeleted(Iterable<InternedPath> paths) {
      boolean changes = false;
      if (!myNeedRescan) {
        for (InternedPath path : paths) {
          changes |= myChanged.remove(path);
          changes |= myDeleted.add(path);
        }
      }
      return changes;
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

  private static final class DelegateFuture implements TaskFuture<Void> {
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
      boolean cancelled = false;
      for (TaskFuture<?> delegate : delegates) {
        cancelled |= delegate.cancel(mayInterruptIfRunning);
      }
      return cancelled;
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
    public Void get() throws InterruptedException, java.util.concurrent.ExecutionException {
      for (Future<?> delegate : getDelegates()) {
        delegate.get();
      }
      return null;
    }

    @Override
    public Void get(long timeout, @NotNull TimeUnit unit)
      throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException {
      ConcurrencyUtil.getAll(timeout, unit, getDelegates());
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
