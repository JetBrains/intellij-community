// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.BuildBundle;
import com.intellij.build.BuildConsoleUtils;
import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildEventDispatcher;
import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildTreeFilters;
import com.intellij.build.BuildView;
import com.intellij.build.BuildViewManager;
import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.BuildViewSettingsProviderAdapter;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.WeakFilterableSupplier;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.BackendExecutionEnvironmentProxy;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentProxy;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtensionManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.task.RunConfigurationTaskState;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.function.Supplier;

import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.createFailureResult;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.getConsoleManagerFor;
import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY;

public class ExternalSystemRunnableState extends UserDataHolderBase implements RunProfileState {

  private static final Logger LOG = Logger.getInstance(ExternalSystemRunnableState.class);

  @ApiStatus.Internal
  public static final Key<ProgressIndicator> PROGRESS_INDICATOR_KEY = Key.create("PROGRESS_INDICATOR");
  @ApiStatus.Internal
  public static final Key<Integer> DEBUGGER_DISPATCH_PORT_KEY = Key.create("DEBUGGER_DISPATCH_PORT");
  @ApiStatus.Internal
  public static final Key<String> DEBUGGER_PARAMETERS_KEY = Key.create("DEBUGGER_PARAMETERS");
  @ApiStatus.Internal
  public static final Key<String> DEBUGGER_DISPATCH_ADDR_KEY = Key.create("DEBUGGER_DISPATCH_ADDR");
  @ApiStatus.Internal
  public static final Key<Integer> BUILD_PROCESS_DEBUGGER_PORT_KEY = Key.create("BUILD_PROCESS_DEBUGGER_PORT");
  @ApiStatus.Internal
  public static final @NotNull Key<ExternalSystemTaskNotificationListener> TASK_NOTIFICATION_LISTENER_KEY =
    Key.create("TASK_NOTIFICATION_LISTENER");
  @ApiStatus.Internal
  public static final Key<ThreeState> NAVIGATE_TO_ERROR_KEY = Key.create("NAVIGATE_TO_ERROR");

  private static final @NotNull String DEFAULT_TASK_PREFIX = ": ";
  private static final @NotNull String DEFAULT_TASK_POSTFIX = "";

  private final @NotNull ExternalSystemTaskExecutionSettings mySettings;
  private final @NotNull Project myProject;
  private final @NotNull ExternalSystemRunConfiguration myConfiguration;
  private final @NotNull ExecutionEnvironment myEnv;
  private @Nullable RunContentDescriptor myContentDescriptor;

  private final int myDebugPort;
  private ServerSocket myForkSocket = null;

  public ExternalSystemRunnableState(@NotNull ExternalSystemTaskExecutionSettings settings,
                                     @NotNull Project project,
                                     boolean debug,
                                     @NotNull ExternalSystemRunConfiguration configuration,
                                     @NotNull ExecutionEnvironment env) {
    mySettings = settings;
    myProject = project;
    myConfiguration = configuration;
    myEnv = env;
    int port;
    if (debug) {
      try {
        port = NetUtils.findAvailableSocketPort();
      }
      catch (IOException e) {
        LOG.warn("Unexpected I/O exception occurred on attempt to find a free port to use for external system task debugging", e);
        port = 0;
      }
    }
    else {
      port = 0;
    }
    myDebugPort = port;
  }

  public int getDebugPort() {
    return myDebugPort;
  }

  public @Nullable ServerSocket getForkSocket() {
    if (myForkSocket == null && !Boolean.getBoolean("external.system.disable.fork.debugger")) {
      try {
        boolean isRemoteRun = ContainerUtil.exists(
          ExternalSystemExecutionAware.getExtensions(mySettings.getExternalSystemId()),
          aware -> aware.isRemoteRun(myConfiguration, myProject)
        );
        myForkSocket = new ServerSocket(0, 0, findAddress(isRemoteRun));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return myForkSocket;
  }

  private static InetAddress findAddress(boolean remoteRun) throws UnknownHostException, SocketException {
    if (remoteRun) {
      String host = Registry.stringValue("external.system.remote.debugger.dispatch.host");
      if (!StringUtil.isEmpty(host) && !"0".equals(host)) {
        return InetAddress.getByName(host);
      }
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
        try {
          boolean isLoopbackInterface = networkInterface.isLoopback();
          if (isLoopbackInterface) continue;
          Enumeration<InetAddress> candidates = networkInterface.getInetAddresses();
          while (candidates.hasMoreElements()) {
            InetAddress candidate = candidates.nextElement();
            if (candidate instanceof Inet4Address && !candidate.isLoopbackAddress()) {
              return candidate;
            }
          }
        }
        catch (SocketException e) {
          LOG.debug("Error while querying interface " + networkInterface + " for IP addresses", e);
        }
      }
    }
    return InetAddress.getByName("127.0.0.1");
  }

  public boolean isReattachDebugProcess() {
    return myConfiguration.isReattachDebugProcess();
  }

  public boolean isDebugServerProcess() {
    return myConfiguration.isDebugServerProcess();
  }

  @Override
  public @Nullable ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    if (myProject.isDisposed()) return null;

    if (!ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(myProject, mySettings.getExternalSystemId())) {
      var externalSystemName = mySettings.getExternalSystemId().getReadableName();
      throw new ExecutionException(ExternalSystemBundle.message("untrusted.project.notification.execution.error", externalSystemName));
    }

    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    FileDocumentManager.getInstance().saveAllDocuments();

    var task = new ExternalSystemExecuteTaskTask(myProject, mySettings, getJvmAgentsSetup(), myConfiguration);
    copyUserDataTo(task);
    addDebugUserDataTo(task);

    var listener = myEnv.getUserData(TASK_NOTIFICATION_LISTENER_KEY);
    if (listener != null) {
      ExternalSystemProgressNotificationManager.getInstance()
        .addNotificationListener(task.getId(), listener);
    }

    var processHandler = new ExternalSystemProcessHandler(task, getExecutionName());

    var consoleManager = getConsoleManagerFor(task);
    var consoleView = consoleManager.attachExecutionConsole(myProject, task, myEnv, processHandler);
    if (consoleView == null) {
      Disposer.register(myProject, processHandler);
    }
    else {
      Disposer.register(myProject, consoleView);
      Disposer.register(consoleView, processHandler);
    }

    var buildDescriptor = createBuildDescriptor(task, processHandler, consoleManager, consoleView);

    var customProgressListener = ObjectUtils.doIfNotNull(task.getUserData(PROGRESS_LISTENER_KEY), it -> myProject.getService(it));
    var progressListener = ObjectUtils.notNull(customProgressListener, () -> createBuildView(buildDescriptor, consoleView));

    ExternalSystemRunConfigurationExtensionManager.getInstance()
      .attachExtensionsToProcess(myConfiguration, processHandler, myEnv.getRunnerSettings());

    BackgroundTaskUtil.executeOnPooledThread(processHandler, () -> {
      var progressIndicator = ObjectUtils.notNull(myEnv.getUserData(PROGRESS_INDICATOR_KEY), () -> new EmptyProgressIndicator());
      var dataContext = BuildConsoleUtils.getDataContext(task.getId(), progressListener, consoleView);
      var startDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
      var settingsDescription = StringUtil.isEmpty(mySettings.toString()) ? "" : String.format(" '%s'", mySettings);
      var greeting = ExternalSystemBundle.message("run.text.starting.task", startDateTime, settingsDescription) + "\n";
      processHandler.notifyTextAvailable(greeting + "\n", ProcessOutputTypes.SYSTEM);
      try (var eventDispatcher = new ExternalSystemEventDispatcher(task.getId(), progressListener, false)) {
        ExternalSystemTelemetryUtil.runWithSpan(mySettings.getExternalSystemId(), "ExternalSystemTaskExecution", _ ->
          task.execute(progressIndicator, new ExternalSystemTaskEventMulticaster(
            processHandler, eventDispatcher, buildDescriptor, dataContext,
            consoleManager, consoleView, settingsDescription
          ))
        );
        ExternalSystemTelemetryUtil.runWithSpan(mySettings.getExternalSystemId(), "ExternalSystemTaskResultProcessing", _ ->
          handleTaskResult(task, eventDispatcher)
        );
      }
    });

    return getExecutionResult(executor, progressListener, consoleView, processHandler, buildDescriptor);
  }

  private @NotNull DefaultExecutionResult getExecutionResult(
    @NotNull Executor executor,
    @NotNull BuildProgressListener progressListener,
    @Nullable ExecutionConsole consoleView,
    @NotNull ExternalSystemProcessHandler processHandler,
    @NotNull DefaultBuildDescriptor buildDescriptor
  ) {
    var executionConsole = progressListener instanceof ExecutionConsole ? (ExecutionConsole)progressListener : consoleView;

    var actions = new ArrayList<AnAction>();
    if (executionConsole instanceof BuildView buildView) {
      ContainerUtil.addAll(actions, buildView.getSwitchActions());
      actions.add(BuildTreeFilters.createFilteringActionsGroup(new WeakFilterableSupplier<>(buildView)));
    }
    var taskState = myConfiguration.getUserData(RunConfigurationTaskState.getKEY());
    if (taskState != null && consoleView != null) {
      actions.addAll(taskState.createCustomActions(processHandler, consoleView, executor));
    }

    var executionResult = new DefaultExecutionResult(executionConsole, processHandler, actions.toArray(AnAction.EMPTY_ARRAY));
    executionResult.setRestartActions(buildDescriptor.getRestartActions().toArray(AnAction.EMPTY_ARRAY));
    return executionResult;
  }

  private @NotNull DefaultBuildDescriptor createBuildDescriptor(
    @NotNull ExternalSystemExecuteTaskTask task,
    @NotNull ExternalSystemProcessHandler processHandler,
    @NotNull ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler> consoleManager,
    @Nullable ExecutionConsole consoleView
  ) {
    var taskId = task.getId();
    var externalProjectPath = task.getExternalProjectPath();
    return new DefaultBuildDescriptor(taskId, processHandler.getExecutionName(), externalProjectPath, System.currentTimeMillis())
      .withNavigateToError(ObjectUtils.notNull(myEnv.getUserData(NAVIGATE_TO_ERROR_KEY), ThreeState.UNSURE))
      .withExecutionFilters(consoleManager.getCustomExecutionFilters(myProject, task, myEnv))
      .withProcessHandler(processHandler, null)
      .withContentDescriptor(() -> myContentDescriptor)
      .withRestartAction(new TaskRerunAction(task))
      .withActions(consoleView == null ? AnAction.EMPTY_ARRAY : consoleManager.getCustomActions(myProject, task, myEnv))
      .withRestartActions(consoleView == null ? AnAction.EMPTY_ARRAY : consoleManager.getRestartActions(consoleView))
      .withContextActions(consoleView == null ? AnAction.EMPTY_ARRAY : consoleManager.getCustomContextActions(myProject, task, myEnv))
      .withExecutionEnvironment(myEnv);
  }

  private @NotNull @Nls String getExecutionName() {
    if (StringUtil.isNotEmpty(mySettings.getExecutionName())) {
      return mySettings.getExecutionName();
    }
    if (StringUtil.isNotEmpty(myConfiguration.getName())) {
      return myConfiguration.getName();
    }
    return AbstractExternalSystemTaskConfigurationType.generateName(
      myProject,
      mySettings.getExternalSystemId(), mySettings.getExternalProjectPath(),
      mySettings.getTaskNames(), mySettings.getExecutionName(),
      DEFAULT_TASK_PREFIX, DEFAULT_TASK_POSTFIX
    );
  }

  private static void handleTaskResult(
    @NotNull ExternalSystemExecuteTaskTask task,
    @NotNull BuildEventDispatcher eventDispatcher
  ) {
    var taskId = task.getId();
    var taskError = task.getError();
    if (taskError == null || taskError instanceof Exception) {
      return;
    }
    var eventMessage = BuildBundle.message("build.status.failed");
    var eventResult = new FailureResultImpl(taskError);
    eventDispatcher.onEvent(taskId, FinishBuildEvent.builder(taskId, eventMessage, eventResult).build());
  }

  private void addDebugUserDataTo(UserDataHolderBase holder) {
    if (myDebugPort > 0) {
      holder.putUserData(BUILD_PROCESS_DEBUGGER_PORT_KEY, myDebugPort);
      ServerSocket forkSocket = getForkSocket();
      if (forkSocket != null) {
        holder.putUserData(DEBUGGER_DISPATCH_ADDR_KEY, forkSocket.getInetAddress().getHostAddress());
        holder.putUserData(DEBUGGER_DISPATCH_PORT_KEY, forkSocket.getLocalPort());
      }
    }
  }

  private @Nullable String getJvmAgentsSetup() throws ExecutionException {
    var extensionsJP = new SimpleJavaParameters();
    var runConfigurationExtensionManager = ExternalSystemRunConfigurationExtensionManager.getInstance();
    runConfigurationExtensionManager.updateVMParameters(myConfiguration, extensionsJP, myEnv.getRunnerSettings(), myEnv.getExecutor());

    var allVMParameters = new ParametersList();
    var data = myEnv.getUserData(ExternalSystemTaskExecutionSettings.JVM_AGENT_SETUP_KEY);
    if (data != null) {
      for (String parameter : data.getList()) {
        if (parameter.startsWith("-agentlib:")) continue;
        if (parameter.startsWith("-agentpath:")) continue;
        if (parameter.startsWith("-javaagent:")) continue;
        throw new ExecutionException(ExternalSystemBundle.message("run.invalid.jvm.agent.configuration", parameter));
      }
      allVMParameters.addAll(data.getParameters());
    }
    allVMParameters.addAll(extensionsJP.getVMParametersList().getParameters());
    return StringUtil.nullize(allVMParameters.getParametersString());
  }

  protected @NotNull BuildView createBuildView(DefaultBuildDescriptor buildDescriptor, ExecutionConsole executionConsole) {
    ExternalSystemRunConfigurationViewManager viewManager = myProject.getService(ExternalSystemRunConfigurationViewManager.class);
    return new BuildView(myProject, executionConsole, buildDescriptor, "build.toolwindow.run.selection.state", viewManager) {
      @Override
      public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        super.onEvent(buildId, event);
        viewManager.onEvent(buildId, event);
      }
    };
  }

  public void setContentDescriptor(@Nullable RunContentDescriptor contentDescriptor) {
    myContentDescriptor = contentDescriptor;
    if (contentDescriptor != null) {
      contentDescriptor.setExecutionId(myEnv.getExecutionId());
      RunnerAndConfigurationSettings settings = myEnv.getRunnerAndConfigurationSettings();
      if (settings != null) {
        contentDescriptor.setActivateToolWindowWhenAdded(settings.isActivateToolWindowBeforeRun());
        contentDescriptor.setAutoFocusContent(settings.isFocusToolWindowBeforeRun());
      }
    }
  }

  private class TaskRerunAction extends FakeRerunAction {

    private final @NotNull ExternalSystemExecuteTaskTask myTask;

    TaskRerunAction(@NotNull ExternalSystemExecuteTaskTask task) {
      myTask = task;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getPresentation().setText(getText());
      event.getPresentation().setIcon(getIcon(event));
      event.getPresentation().setEnabled(isEnabled(event));
    }

    private @NotNull Supplier<String> getText() {
      var executionName = StringUtil.escapeMnemonics(myEnv.getRunProfile().getName());
      return ExecutionBundle.messagePointer("rerun.configuration.action.name", executionName);
    }

    private @NotNull Icon getIcon(@NotNull AnActionEvent event) {
      var descriptor = getDescriptor(event);
      if (descriptor != null && ExecutionManagerImpl.isProcessRunning(descriptor)) {
        return AllIcons.Actions.Restart;
      }
      if (BuildViewManager.class.equals(myTask.getUserData(PROGRESS_LISTENER_KEY))) {
        return AllIcons.Actions.Compile;
      }
      return myEnv.getExecutor().getIcon();
    }

    @Override
    protected @Nullable RunContentDescriptor getDescriptor(AnActionEvent event) {
      return myContentDescriptor != null ? myContentDescriptor : super.getDescriptor(event);
    }

    @Override
    protected @NotNull ExecutionEnvironmentProxy getEnvironmentProxy(@NotNull AnActionEvent event) {
      return new BackendExecutionEnvironmentProxy(myEnv);
    }
  }

  private class ExternalSystemTaskEventMulticaster implements ExternalSystemTaskNotificationListener {

    private final @NotNull ExternalSystemProcessHandler myProcessHandler;
    private final @NotNull BuildEventDispatcher myEventDispatcher;
    private final @NotNull BuildDescriptor myBuildDescriptor;
    private final @NotNull DataContext myDataContext;
    private final @NotNull ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler> myConsoleManager;
    private final @Nullable ExecutionConsole myConsoleView;
    private final @NotNull String mySettingsDescription;

    private ExternalSystemTaskEventMulticaster(
      @NotNull ExternalSystemProcessHandler processHandler,
      @NotNull BuildEventDispatcher eventDispatcher,
      @NotNull BuildDescriptor buildDescriptor,
      @NotNull DataContext dataContext,
      @NotNull ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler> consoleManager,
      @Nullable ExecutionConsole consoleView,
      @NotNull String settingsDescription
    ) {
      myProcessHandler = processHandler;
      myEventDispatcher = eventDispatcher;
      myBuildDescriptor = buildDescriptor;
      myDataContext = dataContext;
      myConsoleManager = consoleManager;
      myConsoleView = consoleView;
      mySettingsDescription = settingsDescription;
    }

    @Override
    public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
      var eventMessage = BuildBundle.message("build.status.running");
      var viewSettingsProvider = ObjectUtils.doIfCast(myConsoleView, BuildViewSettingsProvider.class, BuildViewSettingsProviderAdapter::new);
      myEventDispatcher.onEvent(id,
        StartBuildEvent.builder(eventMessage, myBuildDescriptor)
          .withBuildViewSettings(viewSettingsProvider)
          .build()
      );
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType outputType) {
      if (myConsoleView != null) {
        myConsoleManager.onOutput(myConsoleView, myProcessHandler, text, outputType);
      }
      else {
        myProcessHandler.notifyTextAvailable(text, outputType);
      }
      myEventDispatcher.setStdOut(outputType.isStdout());
      myEventDispatcher.append(text);
    }

    @Override
    public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) {
      var eventMessage = BuildBundle.message("build.status.failed");
      var title = myProcessHandler.getExecutionName() + " " + BuildBundle.message("build.status.failed");
      var externalSystemId = id.getProjectSystemId();
      var externalProjectPath = mySettings.getExternalProjectPath();
      var eventResult = createFailureResult(title, exception, externalSystemId, myProject, externalProjectPath, myDataContext);
      myEventDispatcher.onEvent(id, FinishBuildEvent.builder(id, eventMessage, eventResult).build());
      myProcessHandler.notifyProcessTerminated(1);
    }

    @Override
    public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
      var eventMessage = BuildBundle.message("build.status.cancelled");
      var eventResult = new FailureResultImpl();
      myEventDispatcher.onEvent(id, FinishBuildEvent.builder(id, eventMessage, eventResult).build());
      myProcessHandler.notifyProcessTerminated(1);
    }

    @Override
    public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
      var eventMessage = BuildBundle.message("build.event.message.successful");
      var eventResult = new SuccessResultImpl();
      myEventDispatcher.onEvent(id, FinishBuildEvent.builder(id, eventMessage, eventResult).build());
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
      if (myConsoleView != null) {
        myConsoleManager.onStatusChange(myConsoleView, event);
      }
      if (event instanceof ExternalSystemBuildEvent buildEvent) {
        myEventDispatcher.onEvent(event.getId(), buildEvent.getBuildEvent());
      }
      else if (event instanceof ExternalSystemTaskExecutionEvent taskExecutionEvent) {
        myEventDispatcher.onEvent(event.getId(), convert(taskExecutionEvent));
      }
    }

    @Override
    public void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) {
      RunConfigurationTaskState taskState = myConfiguration.getUserData(RunConfigurationTaskState.getKEY());
      if (taskState != null && myConsoleView != null) {
        taskState.processExecutionResult(myProcessHandler, myConsoleView);
      }
    }

    @Override
    public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
      final String endDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
      final String farewell = ExternalSystemBundle.message("run.text.ended.task", endDateTime, mySettingsDescription);
      myProcessHandler.notifyTextAvailable(farewell + "\n", ProcessOutputTypes.SYSTEM);
      ExternalSystemRunConfiguration.foldGreetingOrFarewell(myConsoleView, farewell, false);
      myProcessHandler.notifyProcessTerminated(0);
    }
  }
}
