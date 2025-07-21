// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.task.RunConfigurationTaskState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;

import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.*;
import static com.intellij.openapi.util.text.StringUtil.nullize;

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

    ProjectSystemId externalSystemId = mySettings.getExternalSystemId();
    if (!ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(myProject, externalSystemId)) {
      String externalSystemName = externalSystemId.getReadableName();
      throw new ExecutionException(ExternalSystemBundle.message("untrusted.project.notification.execution.error", externalSystemName));
    }

    String jvmParametersSetup = getJvmAgentsSetup();

    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    FileDocumentManager.getInstance().saveAllDocuments();

    ExternalSystemExecuteTaskTask task = new ExternalSystemExecuteTaskTask(myProject, mySettings, jvmParametersSetup, myConfiguration);
    copyUserDataTo(task);
    addDebugUserDataTo(task);
    ExternalSystemTaskNotificationListener listener = myEnv.getUserData(TASK_NOTIFICATION_LISTENER_KEY);
    if (listener != null) {
      ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(task.getId(), listener);
    }

    final String executionName = getExecutionName(externalSystemId);

    final ExternalSystemProcessHandler processHandler = new ExternalSystemProcessHandler(task, executionName);
    final ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler>
      consoleManager = getConsoleManagerFor(task);

    final ExecutionConsole consoleView =
      consoleManager.attachExecutionConsole(myProject, task, myEnv, processHandler);
    AnAction[] customActions, restartActions, contextActions;
    if (consoleView == null) {
      customActions = AnAction.EMPTY_ARRAY;
      restartActions = AnAction.EMPTY_ARRAY;
      contextActions = AnAction.EMPTY_ARRAY;
      Disposer.register(myProject, processHandler);
    }
    else {
      Disposer.register(myProject, consoleView);
      Disposer.register(consoleView, processHandler);
      customActions = consoleManager.getCustomActions(myProject, task, myEnv);
      restartActions = consoleManager.getRestartActions(consoleView);
      contextActions = consoleManager.getCustomContextActions(myProject, task, myEnv);
    }
    DefaultBuildDescriptor buildDescriptor =
      new DefaultBuildDescriptor(task.getId(), executionName, task.getExternalProjectPath(), System.currentTimeMillis());
    Class<? extends BuildProgressListener> progressListenerClazz = task.getUserData(ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY);
    Filter[] filters = consoleManager.getCustomExecutionFilters(myProject, task, myEnv);
    Arrays.stream(filters).forEach(buildDescriptor::withExecutionFilter);
    final BuildProgressListener progressListener =
      progressListenerClazz != null ? myProject.getService(progressListenerClazz)
                                    : createBuildView(buildDescriptor, consoleView);

    var runnerSettings = myEnv.getRunnerSettings();
    var runConfigurationExtensionManager = ExternalSystemRunConfigurationExtensionManager.getInstance();
    runConfigurationExtensionManager.attachExtensionsToProcess(myConfiguration, processHandler, runnerSettings);
    BackgroundTaskUtil.executeOnPooledThread(processHandler, () -> {
      var progressIndicator = myEnv.getUserData(PROGRESS_INDICATOR_KEY);
      if (progressIndicator == null) {
        progressIndicator = new EmptyProgressIndicator();
      }
      executeTask(task, executionName, progressIndicator, processHandler, progressListener, consoleManager, consoleView, buildDescriptor,
                  customActions, restartActions, contextActions);
    });
    ExecutionConsole executionConsole = progressListener instanceof ExecutionConsole ? (ExecutionConsole)progressListener : consoleView;
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (executionConsole instanceof BuildView) {
      actionGroup.addAll(((BuildView)executionConsole).getSwitchActions());
      actionGroup.add(BuildTreeFilters.createFilteringActionsGroup(new WeakFilterableSupplier<>((BuildView)executionConsole)));
    }
    RunConfigurationTaskState taskState = myConfiguration.getUserData(RunConfigurationTaskState.getKEY());
    if (taskState != null && consoleView != null) {
      actionGroup.addAll(taskState.createCustomActions(processHandler, consoleView, executor));
    }

    DefaultExecutionResult executionResult = new DefaultExecutionResult(
      executionConsole, processHandler, actionGroup.getChildren(ActionManager.getInstance()));
    executionResult.setRestartActions(restartActions);
    return executionResult;
  }

  private @NotNull @Nls String getExecutionName(@NotNull ProjectSystemId externalSystemId) {
    if (StringUtil.isNotEmpty(mySettings.getExecutionName())) {
      return mySettings.getExecutionName();
    }
    if (StringUtil.isNotEmpty(myConfiguration.getName())) {
      return myConfiguration.getName();
    }
    return AbstractExternalSystemTaskConfigurationType.generateName(myProject, externalSystemId, mySettings.getExternalProjectPath(),
                                                                    mySettings.getTaskNames(), mySettings.getExecutionName(),
                                                                    DEFAULT_TASK_PREFIX, DEFAULT_TASK_POSTFIX
    );
  }

  private void executeTask(@NotNull ExternalSystemExecuteTaskTask task,
                           @Nls String executionName,
                           @NotNull ProgressIndicator indicator,
                           @NotNull ExternalSystemProcessHandler processHandler,
                           @Nullable BuildProgressListener progressListener,
                           @NotNull ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler> consoleManager,
                           @Nullable ExecutionConsole consoleView,
                           @NotNull DefaultBuildDescriptor buildDescriptor,
                           AnAction[] customActions,
                           AnAction[] restartActions,
                           AnAction[] contextActions) {
    if (indicator instanceof ProgressIndicatorEx indicatorEx) {
      indicatorEx.addStateDelegate(new AbstractProgressIndicatorExBase() {
        @Override
        public void cancel() {
          super.cancel();
          task.cancel();
        }
      });
    }
    final String startDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
    final String settingsDescription = StringUtil.isEmpty(mySettings.toString()) ? "" : String.format(" '%s'", mySettings);
    final String greeting = ExternalSystemBundle.message("run.text.starting.task", startDateTime, settingsDescription) + "\n";
    processHandler.notifyTextAvailable(greeting + "\n", ProcessOutputTypes.SYSTEM);

    try (BuildEventDispatcher eventDispatcher = new ExternalSystemEventDispatcher(task.getId(), progressListener, false)) {
      ExternalSystemTaskNotificationListener taskListener = new ExternalSystemTaskNotificationListener() {
        @Override
        public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          if (progressListener != null) {
            AnAction rerunTaskAction = new ExternalSystemRunConfiguration.MyTaskRerunAction(progressListener, myEnv, myContentDescriptor);
            BuildViewSettingsProvider viewSettingsProvider =
              consoleView instanceof BuildViewSettingsProvider ?
              new BuildViewSettingsProviderAdapter((BuildViewSettingsProvider)consoleView) : null;
            buildDescriptor
              .withProcessHandler(processHandler, view -> ExternalSystemRunConfiguration
                .foldGreetingOrFarewell(consoleView, greeting, true))
              .withContentDescriptor(() -> myContentDescriptor)
              .withActions(customActions)
              .withRestartAction(rerunTaskAction)
              .withRestartActions(restartActions)
              .withContextActions(contextActions)
              .withExecutionEnvironment(myEnv);
            progressListener.onEvent(id,
                                     new StartBuildEventImpl(buildDescriptor, BuildBundle.message("build.status.running"))
                                       .withBuildViewSettingsProvider(viewSettingsProvider));
          }
        }

        @Override
        public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
          if (consoleView != null) {
            consoleManager.onOutput(consoleView, processHandler, text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
          }
          else {
            processHandler.notifyTextAvailable(text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
          }
          eventDispatcher.setStdOut(stdOut);
          eventDispatcher.append(text);
        }

        @Override
        public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) {
          if (progressListener != null) {
            var eventTime = System.currentTimeMillis();
            var eventMessage = BuildBundle.message("build.status.failed");
            var title = executionName + " " + BuildBundle.message("build.status.failed");
            var externalSystemId = id.getProjectSystemId();
            var externalProjectPath = mySettings.getExternalProjectPath();
            var dataContext = BuildConsoleUtils.getDataContext(id, progressListener, consoleView);
            var eventResult = createFailureResult(title, exception, externalSystemId, myProject, externalProjectPath, dataContext);
            eventDispatcher.onEvent(id, new FinishBuildEventImpl(id, null, eventTime, eventMessage, eventResult));
          }
          processHandler.notifyProcessTerminated(1);
        }

        @Override
        public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          eventDispatcher.onEvent(id, new FinishBuildEventImpl(id, null, System.currentTimeMillis(),
                                                               BuildBundle.message("build.status.cancelled"), new FailureResultImpl()));
          processHandler.notifyProcessTerminated(1);
        }

        @Override
        public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          eventDispatcher.onEvent(id, new FinishBuildEventImpl(
            id, null, System.currentTimeMillis(), BuildBundle.message("build.event.message.successful"), new SuccessResultImpl()));
        }

        @Override
        public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
          if (consoleView != null) {
            consoleManager.onStatusChange(consoleView, event);
          }
          if (event instanceof ExternalSystemBuildEvent) {
            eventDispatcher.onEvent(event.getId(), ((ExternalSystemBuildEvent)event).getBuildEvent());
          }
          else if (event instanceof ExternalSystemTaskExecutionEvent) {
            BuildEvent buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
            eventDispatcher.onEvent(event.getId(), buildEvent);
          }
        }

        @Override
        public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          final String endDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
          final String farewell = ExternalSystemBundle.message("run.text.ended.task", endDateTime, settingsDescription);
          processHandler.notifyTextAvailable(farewell + "\n", ProcessOutputTypes.SYSTEM);
          ExternalSystemRunConfiguration.foldGreetingOrFarewell(consoleView, farewell, false);
          processHandler.notifyProcessTerminated(0);
        }

        @Override
        public void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) {
          RunConfigurationTaskState taskState = myConfiguration.getUserData(RunConfigurationTaskState.getKEY());
          if (taskState != null && consoleView != null) {
            taskState.processExecutionResult(processHandler, consoleView);
          }
        }
      };
      task.execute(indicator, taskListener);
      Throwable taskError = task.getError();
      if (taskError != null && !(taskError instanceof Exception)) {
        FinishBuildEventImpl failureEvent = new FinishBuildEventImpl(task.getId(), null, System.currentTimeMillis(),
                                                                     BuildBundle.message("build.status.failed"),
                                                                     new FailureResultImpl(taskError));
        eventDispatcher.onEvent(task.getId(), failureEvent);
      }
    }
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

    String jvmParametersSetup = "";
    final ParametersList allVMParameters = new ParametersList();
    final ParametersList data = myEnv.getUserData(ExternalSystemTaskExecutionSettings.JVM_AGENT_SETUP_KEY);
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
    jvmParametersSetup = allVMParameters.getParametersString();
    return nullize(jvmParametersSetup);
  }

  protected BuildView createBuildView(DefaultBuildDescriptor buildDescriptor, ExecutionConsole executionConsole) {
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
}
