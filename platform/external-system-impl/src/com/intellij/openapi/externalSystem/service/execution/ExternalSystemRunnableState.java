// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtensionManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Enumeration;

import static com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtensionManager.createVMParameters;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.convert;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.getConsoleManagerFor;
import static com.intellij.openapi.util.text.StringUtil.nullize;

public class ExternalSystemRunnableState extends UserDataHolderBase implements RunProfileState {
  @ApiStatus.Internal
  public static final Key<Integer> DEBUGGER_DISPATCH_PORT_KEY = Key.create("DEBUGGER_DISPATCH_PORT");
  @ApiStatus.Internal
  public static final Key<String> DEBUGGER_DISPATCH_ADDR_KEY = Key.create("DEBUGGER_DISPATCH_ADDR");
  @ApiStatus.Internal
  public static final Key<Integer> BUILD_PROCESS_DEBUGGER_PORT_KEY = Key.create("BUILD_PROCESS_DEBUGGER_PORT");

  @NotNull private final ExternalSystemTaskExecutionSettings mySettings;
  @NotNull private final Project myProject;
  @NotNull private final ExternalSystemRunConfiguration myConfiguration;
  @NotNull private final ExecutionEnvironment myEnv;
  @Nullable private RunContentDescriptor myContentDescriptor;

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
        ExternalSystemRunConfiguration.LOG
          .warn("Unexpected I/O exception occurred on attempt to find a free port to use for external system task debugging", e);
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

  @Nullable
  public ServerSocket getForkSocket() {
    if (myForkSocket == null && !ExternalSystemRunConfiguration.DISABLE_FORK_DEBUGGER) {
      try {
        boolean isRemoteRun = ExternalSystemExecutionAware.getExtensions(mySettings.getExternalSystemId()).stream()
          .anyMatch(aware -> aware.isRemoteRun(myConfiguration, myProject));
        myForkSocket = new ServerSocket(0, 0, findAddress(isRemoteRun));
      }
      catch (IOException e) {
        ExternalSystemRunConfiguration.LOG.error(e);
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
          ExternalSystemRunConfiguration.LOG.debug("Error while querying interface " + networkInterface + " for IP addresses", e);
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

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    if (myProject.isDisposed()) return null;

    ProjectSystemId externalSystemId = mySettings.getExternalSystemId();
    if (!ExternalSystemUtil.confirmLoadingUntrustedProject(myProject, externalSystemId)) {
      String externalSystemName = externalSystemId.getReadableName();
      throw new ExecutionException(ExternalSystemBundle.message("untrusted.project.notification.execution.error", externalSystemName));
    }

    String jvmParametersSetup = getJvmParametersSetup();

    ApplicationManager.getApplication().assertIsWriteThread();
    FileDocumentManager.getInstance().saveAllDocuments();

    ExternalSystemExecuteTaskTask task = new ExternalSystemExecuteTaskTask(myProject, mySettings, jvmParametersSetup, myConfiguration);
    copyUserDataTo(task);
    addDebugUserDataTo(task);

    final String executionName = StringUtil.isNotEmpty(mySettings.getExecutionName())
                                 ? mySettings.getExecutionName()
                                 : StringUtil.isNotEmpty(myConfiguration.getName())
                                   ? myConfiguration.getName() : AbstractExternalSystemTaskConfigurationType.generateName(
                                   myProject, externalSystemId, mySettings.getExternalProjectPath(),
                                   mySettings.getTaskNames(), mySettings.getExecutionName(), ": ", "");

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

    ExternalSystemRunConfigurationExtensionManager.attachToProcess(myConfiguration, processHandler, myEnv.getRunnerSettings());

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final String startDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
      final String settingsDescription = StringUtil.isEmpty(mySettings.toString()) ? "" : String.format(" '%s'", mySettings);
      final String greeting = ExternalSystemBundle.message("run.text.starting.task", startDateTime, settingsDescription) + "\n";
      processHandler.notifyTextAvailable(greeting + "\n", ProcessOutputTypes.SYSTEM);

      try (BuildEventDispatcher eventDispatcher = new ExternalSystemEventDispatcher(task.getId(), progressListener, false)) {
        ExternalSystemTaskNotificationListenerAdapter taskListener = new ExternalSystemTaskNotificationListenerAdapter() {
          @Override
          public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
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
          public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
            DataContext dataContext = BuildConsoleUtils.getDataContext(id, progressListener);
            FailureResult failureResult = ExternalSystemUtil.createFailureResult(
              executionName + " " + BuildBundle.message("build.status.failed"), e, id.getProjectSystemId(), myProject, dataContext);
            eventDispatcher.onEvent(id, new FinishBuildEventImpl(id, null, System.currentTimeMillis(),
                                                                 BuildBundle.message("build.status.failed"), failureResult));
            processHandler.notifyProcessTerminated(1);
          }

          @Override
          public void onCancel(@NotNull ExternalSystemTaskId id) {
            eventDispatcher.onEvent(id, new FinishBuildEventImpl(id, null, System.currentTimeMillis(),
                                                                 BuildBundle.message("build.status.cancelled"), new FailureResultImpl()));
            processHandler.notifyProcessTerminated(1);
          }

          @Override
          public void onSuccess(@NotNull ExternalSystemTaskId id) {
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
          public void onEnd(@NotNull ExternalSystemTaskId id) {
            final String endDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
            final String farewell = ExternalSystemBundle.message("run.text.ended.task", endDateTime, settingsDescription);
            processHandler.notifyTextAvailable(farewell + "\n", ProcessOutputTypes.SYSTEM);
            ExternalSystemRunConfiguration.foldGreetingOrFarewell(consoleView, farewell, false);
            processHandler.notifyProcessTerminated(0);
          }
        };
        task.execute(taskListener);
        Throwable taskError = task.getError();
        if (taskError != null && !(taskError instanceof Exception)) {
          FinishBuildEventImpl failureEvent = new FinishBuildEventImpl(task.getId(), null, System.currentTimeMillis(),
                                                                       BuildBundle.message("build.status.failed"),
                                                                       new FailureResultImpl(taskError));
          eventDispatcher.onEvent(task.getId(), failureEvent);
        }
      }
    });
    ExecutionConsole executionConsole = progressListener instanceof ExecutionConsole ? (ExecutionConsole)progressListener : consoleView;
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (executionConsole instanceof BuildView) {
      actionGroup.addAll(((BuildView)executionConsole).getSwitchActions());
      actionGroup.add(BuildTreeFilters.createFilteringActionsGroup((BuildView)executionConsole));
    }
    DefaultExecutionResult executionResult = new DefaultExecutionResult(executionConsole, processHandler, actionGroup.getChildren(null));
    executionResult.setRestartActions(restartActions);
    return executionResult;
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

  @Nullable
  private String getJvmParametersSetup() throws ExecutionException {
    SimpleJavaParameters extensionsJP = createVMParameters(myConfiguration, myEnv.getRunnerSettings(), myEnv.getExecutor());

    String jvmParametersSetup = "";
    if (myDebugPort <= 0) {
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
    }
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
      }
    }
  }

  @Nullable
  public XDebugProcess startDebugProcess(@NotNull XDebugSession session,
                                         @NotNull ExecutionEnvironment env) {
    return null;
  }
}
