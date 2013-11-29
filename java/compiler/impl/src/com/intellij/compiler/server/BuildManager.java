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
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.impl.BuildProcessClasspathManager;
import com.intellij.execution.ExecutionAdapter;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
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
import com.intellij.openapi.vfs.newvfs.impl.FileNameCache;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.NetUtils;
import gnu.trove.THashSet;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.io.ChannelRegistrar;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.jps.api.*;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/6/11
 */
public class BuildManager implements ApplicationComponent{
  public static final Key<Boolean> ALLOW_AUTOMAKE = Key.create("_allow_automake_when_process_is_active_");
  private static final Key<String> FORCE_MODEL_LOADING_PARAMETER = Key.create(BuildParametersKeys.FORCE_MODEL_LOADING);

  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildManager");
  private static final String COMPILER_PROCESS_JDK_PROPERTY = "compiler.process.jdk";
  public static final String SYSTEM_ROOT = "compile-server";
  public static final String TEMP_DIR_NAME = "_temp_";
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

  private final Map<RequestFuture, Project> myAutomakeFutures = Collections.synchronizedMap(new HashMap<RequestFuture, Project>());
  private final Map<String, RequestFuture> myBuildsInProgress = Collections.synchronizedMap(new HashMap<String, RequestFuture>());
  private final BuildProcessClasspathManager myClasspathManager = new BuildProcessClasspathManager();
  private final SequentialTaskExecutor myRequestsProcessor = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);
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

  private final ChannelRegistrar myChannelRegistrar = new ChannelRegistrar();

  private final BuildMessageDispatcher myMessageDispatcher = new BuildMessageDispatcher();
  private volatile int myListenPort = -1;
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

        Project project = null;
        ProjectFileIndex fileIndex = null;

        for (VFileEvent event : events) {
          final VirtualFile eventFile = event.getFile();
          if (eventFile == null || ProjectCoreUtil.isProjectOrWorkspaceFile(eventFile)) {
            continue;
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
            return true;
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
    final RequestFuture future = scheduleBuild(
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
    if (!config.useOutOfProcessBuild() || !config.MAKE_PROJECT_ON_SAVE) {
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
    for (RunContentDescriptor descriptor : ExecutionManager.getInstance(project).getContentManager().getAllDescriptors()) {
      final ProcessHandler handler = descriptor.getProcessHandler();
      if (handler != null && !handler.isProcessTerminated() && !ALLOW_AUTOMAKE.get(handler, Boolean.FALSE)) { // active process
        return true;
      }
    }
    return false;
  }

  public Collection<RequestFuture> cancelAutoMakeTasks(Project project) {
    final Collection<RequestFuture> futures = new SmartList<RequestFuture>();
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
    final Map<String, String> userData, final DefaultMessageHandler messageHandler) {

    final String projectPath = getProjectPath(project);
    final UUID sessionId = UUID.randomUUID();
    final boolean isAutomake = messageHandler instanceof AutoMakeMessageHandler;
    final BuilderMessageHandler handler = new MessageHandlerWrapper(messageHandler) {
      @Override
      public void buildStarted(UUID sessionId) {
        super.buildStarted(sessionId);
        try {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).buildStarted(project, sessionId, isAutomake);
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
            ApplicationManager.getApplication().getMessageBus().syncPublisher(BuildManagerListener.TOPIC).buildFinished(project, sessionId, isAutomake);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      }
    };
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

          final CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings globals =
            CmdlineRemoteProto.Message.ControllerMessage.GlobalSettings.newBuilder()
              .setGlobalOptionsPath(PathManager.getOptionsPath())
              .build();
          CmdlineRemoteProto.Message.ControllerMessage.FSEvent currentFSChanges;
          final SequentialTaskExecutor projectTaskQueue;
          synchronized (myProjectDataMap) {
            ProjectData data = myProjectDataMap.get(projectPath);
            if (data == null) {
              data = new ProjectData(new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE));
              myProjectDataMap.put(projectPath, data);
            }
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
            currentFSChanges = data.getAndResetRescanFlag() ? null : data.createNextEvent();
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
            params = CmdlineProtoUtil.createBuildRequest(projectPath, scopes, isMake ? Collections.<String>emptyList() : paths,
                                                         userData, globals, currentFSChanges);
          }

          myMessageDispatcher.registerBuildMessageHandler(sessionId, new MessageHandlerWrapper(handler) {
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
      }

      // validate tools.jar presence
      final JavaSdkType projectJdkType = (JavaSdkType)projectJdk.getSdkType();
      if (projectJdk.equals(internalJdk)) {
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

    final CompilerWorkspaceConfiguration config = CompilerWorkspaceConfiguration.getInstance(project);
    final GeneralCommandLine cmdLine = new GeneralCommandLine();
    cmdLine.setExePath(vmExecutablePath);
    //cmdLine.addParameter("-XX:MaxPermSize=150m");
    //cmdLine.addParameter("-XX:ReservedCodeCacheSize=64m");
    final int heapSize = config.getProcessHeapSize(JavacConfiguration.getOptions(project, JavacConfiguration.class).MAXIMUM_HEAP_SIZE);

    cmdLine.addParameter("-Xmx" + heapSize + "m");

    if (SystemInfo.isMac && sdkVersion != null && JavaSdkVersion.JDK_1_6.equals(sdkVersion) && Registry.is("compiler.process.32bit.vm.on.mac")) {
      // unfortunately -d32 is supported on jdk 1.6 only
      cmdLine.addParameter("-d32");
    }

    cmdLine.addParameter("-Djava.awt.headless=true");
    cmdLine.addParameter("-Djava.endorsed.dirs=\"\""); // turn off all jre customizations for predictable behaviour
    if (IS_UNIT_TEST_MODE) {
      cmdLine.addParameter("-Dtest.mode=true");
    }
    cmdLine.addParameter("-Djdt.compiler.useSingleThread=true"); // always run eclipse compiler in single-threaded mode

    final String shouldGenerateIndex = System.getProperty(GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION);
    if (shouldGenerateIndex != null) {
      cmdLine.addParameter("-D"+ GlobalOptions.GENERATE_CLASSPATH_INDEX_OPTION +"=" + shouldGenerateIndex);
    }
    cmdLine.addParameter("-D"+ GlobalOptions.COMPILE_PARALLEL_OPTION +"=" + Boolean.toString(config.PARALLEL_COMPILATION));
    cmdLine.addParameter("-D"+ GlobalOptions.REBUILD_ON_DEPENDENCY_CHANGE_OPTION + "=" + Boolean.toString(config.REBUILD_ON_DEPENDENCY_CHANGE));

    if (Boolean.TRUE.equals(Boolean.valueOf(System.getProperty("java.net.preferIPv4Stack", "false")))) {
      cmdLine.addParameter("-Djava.net.preferIPv4Stack=true");
    }

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

    if (!Registry.is("compiler.process.use.memory.temp.cache")) {
      cmdLine.addParameter("-D"+ GlobalOptions.USE_MEMORY_TEMP_CACHE_OPTION + "=false");
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

    cmdLine.addParameter("-D" + GlobalOptions.LOG_DIR_OPTION + "=" + FileUtil.toSystemIndependentName(getBuildLogDirectory().getAbsolutePath()));

    final File workDirectory = getBuildSystemDirectory();
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
    cmdLine.addParameter("-classpath");
    cmdLine.addParameter(classpathToString(launcherCp));
    
    cmdLine.addParameter(launcherClass.getName());

    final List<String> cp = ClasspathBootstrap.getBuildProcessApplicationClasspath(true);
    cp.addAll(myClasspathManager.getBuildProcessPluginsClasspath(project));
    if (isProfilingMode) {
      cp.add(new File(workDirectory, "yjp-controller-api-redist.jar").getPath());
      cmdLine.addParameter("-agentlib:yjpagent=disablej2ee,disablealloc,delay=10000,sessionname=ExternalBuild");
    }
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

  public File getBuildLogDirectory() {
    return new File(PathManager.getLogPath(), "build-log");
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
    private final Set<InternedPath> myChanged = new THashSet<InternedPath>();
    private final Set<InternedPath> myDeleted = new THashSet<InternedPath>();
    private long myNextEventOrdinal = 0L;
    private boolean myNeedRescan = true;

    private ProjectData(SequentialTaskExecutor taskQueue) {
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
        final String name = FileNameCache.getVFileName(myPath[0]);
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
  
}
