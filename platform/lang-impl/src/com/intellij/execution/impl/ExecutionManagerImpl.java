/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.CommonBundle;
import com.intellij.execution.*;
import com.intellij.execution.configuration.CompatibilityAwareRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class ExecutionManagerImpl extends ExecutionManager implements Disposable {
  public static final Key<Object> EXECUTION_SESSION_ID_KEY = Key.create("EXECUTION_SESSION_ID_KEY");
  public static final Key<Boolean> EXECUTION_SKIP_RUN = Key.create("EXECUTION_SKIP_RUN");

  protected static final Logger LOG = Logger.getInstance(ExecutionManagerImpl.class);
  private static final ProcessHandler[] EMPTY_PROCESS_HANDLERS = new ProcessHandler[0];

  private final Project myProject;
  private final Alarm myAwaitingTerminationAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Map<RunProfile, ExecutionEnvironment> myAwaitingRunProfiles = ContainerUtil.newHashMap();
  protected final List<Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor>> myRunningConfigurations =
    ContainerUtil.createLockFreeCopyOnWriteList();
  private RunContentManagerImpl myContentManager;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  @NotNull
  public static ExecutionManagerImpl getInstance(@NotNull Project project) {
    return (ExecutionManagerImpl)ServiceManager.getService(project, ExecutionManager.class);
  }

  protected ExecutionManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  private static ExecutionEnvironmentBuilder createEnvironmentBuilder(@NotNull Project project,
                                                                      @NotNull Executor executor,
                                                                      @Nullable RunnerAndConfigurationSettings configuration) {
    ExecutionEnvironmentBuilder builder = new ExecutionEnvironmentBuilder(project, executor);

    ProgramRunner runner =
      RunnerRegistry.getInstance().getRunner(executor.getId(), configuration != null ? configuration.getConfiguration() : null);
    if (runner == null && configuration != null) {
      LOG.error("Cannot find runner for " + configuration.getName());
    }
    else if (runner != null) {
      assert configuration != null;
      builder.runnerAndSettings(runner, configuration);
    }
    return builder;
  }

  public static boolean isProcessRunning(@Nullable RunContentDescriptor descriptor) {
    ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
    return processHandler != null && !processHandler.isProcessTerminated();
  }

  private static void start(@NotNull ExecutionEnvironment environment) {
    //start() can be called during restartRunProfile() after pretty long 'awaitTermination()' so we have to check if the project is still here
    if (environment.getProject().isDisposed()) return;

    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    ProgramRunnerUtil.executeConfiguration(environment, settings != null && settings.isEditBeforeRun(), true);
  }

  private static boolean userApprovesStopForSameTypeConfigurations(Project project, String configName, int instancesCount) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    final RunManagerConfig config = runManager.getConfig();
    if (!config.isRestartRequiresConfirmation()) return true;

    DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return config.isRestartRequiresConfirmation();
      }

      @Override
      public void setToBeShown(boolean value, int exitCode) {
        config.setRestartRequiresConfirmation(value);
      }

      @Override
      public boolean canBeHidden() {
        return true;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return false;
      }

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return CommonBundle.message("dialog.options.do.not.show");
      }
    };
    return Messages.showOkCancelDialog(
      project,
      ExecutionBundle.message("rerun.singleton.confirmation.message", configName, instancesCount),
      ExecutionBundle.message("process.is.running.dialog.title", configName),
      ExecutionBundle.message("rerun.confirmation.button.text"),
      CommonBundle.message("button.cancel"),
      Messages.getQuestionIcon(), option) == Messages.OK;
  }

  private static boolean userApprovesStopForIncompatibleConfigurations(Project project,
                                                                       String configName,
                                                                       List<RunContentDescriptor> runningIncompatibleDescriptors) {
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    final RunManagerConfig config = runManager.getConfig();
    if (!config.isStopIncompatibleRequiresConfirmation()) return true;

    DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return config.isStopIncompatibleRequiresConfirmation();
      }

      @Override
      public void setToBeShown(boolean value, int exitCode) {
        config.setStopIncompatibleRequiresConfirmation(value);
      }

      @Override
      public boolean canBeHidden() {
        return true;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return false;
      }

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return CommonBundle.message("dialog.options.do.not.show");
      }
    };

    final StringBuilder names = new StringBuilder();
    for (final RunContentDescriptor descriptor : runningIncompatibleDescriptors) {
      String name = descriptor.getDisplayName();
      if (names.length() > 0) {
        names.append(", ");
      }
      names.append(StringUtil.isEmpty(name) ? ExecutionBundle.message("run.configuration.no.name")
                                            : String.format("'%s'", name));
    }

    //noinspection DialogTitleCapitalization
    return Messages.showOkCancelDialog(
      project,
      ExecutionBundle.message("stop.incompatible.confirmation.message",
                              configName, names.toString(), runningIncompatibleDescriptors.size()),
      ExecutionBundle.message("incompatible.configuration.is.running.dialog.title", runningIncompatibleDescriptors.size()),
      ExecutionBundle.message("stop.incompatible.confirmation.button.text"),
      CommonBundle.message("button.cancel"),
      Messages.getQuestionIcon(), option) == Messages.OK;
  }

  public static void stopProcess(@Nullable RunContentDescriptor descriptor) {
    stopProcess(descriptor != null ? descriptor.getProcessHandler() : null);
  }

  public static void stopProcess(@Nullable ProcessHandler processHandler) {
    if (processHandler == null) {
      return;
    }

    processHandler.putUserData(ProcessHandler.TERMINATION_REQUESTED, Boolean.TRUE);

    if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating()) {
      // process termination was requested, but it's still alive
      // in this case 'force quit' will be performed
      ((KillableProcess)processHandler).killProcess();
      return;
    }

    if (!processHandler.isProcessTerminated()) {
      if (processHandler.detachIsDefault()) {
        processHandler.detachProcess();
      }
      else {
        processHandler.destroyProcess();
      }
    }
  }

  @Override
  public void dispose() {
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      Disposer.dispose(trinity.first);
    }
    myRunningConfigurations.clear();
  }

  @NotNull
  @Override
  public RunContentManager getContentManager() {
    if (myContentManager == null) {
      myContentManager = new RunContentManagerImpl(myProject, DockManager.getInstance(myProject));
      Disposer.register(myProject, myContentManager);
    }
    return myContentManager;
  }

  @NotNull
  @Override
  public ProcessHandler[] getRunningProcesses() {
    if (myContentManager == null) return EMPTY_PROCESS_HANDLERS;
    List<ProcessHandler> handlers = null;
    for (RunContentDescriptor descriptor : getContentManager().getAllDescriptors()) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null) {
        if (handlers == null) {
          handlers = new SmartList<>();
        }
        handlers.add(processHandler);
      }
    }
    return handlers == null ? EMPTY_PROCESS_HANDLERS : handlers.toArray(new ProcessHandler[handlers.size()]);
  }

  @Override
  public void compileAndRun(@NotNull final Runnable startRunnable,
                            @NotNull final ExecutionEnvironment environment,
                            @Nullable final RunProfileState state,
                            @Nullable final Runnable onCancelRunnable) {
    long id = environment.getExecutionId();
    if (id == 0) {
      id = environment.assignNewExecutionId();
    }

    RunProfile profile = environment.getRunProfile();
    if (!(profile instanceof RunConfiguration)) {
      startRunnable.run();
      return;
    }

    final RunConfiguration runConfiguration = (RunConfiguration)profile;
    final List<BeforeRunTask> beforeRunTasks = RunManagerEx.getInstanceEx(myProject).getBeforeRunTasks(runConfiguration);
    if (beforeRunTasks.isEmpty()) {
      startRunnable.run();
    }
    else {
      DataContext context = environment.getDataContext();
      final DataContext projectContext = context != null ? context : SimpleDataContext.getProjectContext(myProject);
      final long finalId = id;
      final Long executionSessionId = new Long(id);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (BeforeRunTask task : beforeRunTasks) {
          if (myProject.isDisposed()) {
            return;
          }
          @SuppressWarnings("unchecked")
          BeforeRunTaskProvider<BeforeRunTask> provider = BeforeRunTaskProvider.getProvider(myProject, task.getProviderId());
          if (provider == null) {
            LOG.warn("Cannot find BeforeRunTaskProvider for id='" + task.getProviderId() + "'");
            continue;
          }
          ExecutionEnvironment taskEnvironment = new ExecutionEnvironmentBuilder(environment).contentToReuse(null).build();
          taskEnvironment.setExecutionId(finalId);
          EXECUTION_SESSION_ID_KEY.set(taskEnvironment, executionSessionId);
          if (!provider.executeTask(projectContext, runConfiguration, taskEnvironment, task)) {
            if (onCancelRunnable != null) {
              SwingUtilities.invokeLater(onCancelRunnable);
            }
            return;
          }
        }

        doRun(environment, startRunnable);
      });
    }
  }

  protected void doRun(@NotNull final ExecutionEnvironment environment, @NotNull final Runnable startRunnable) {
    Boolean allowSkipRun = environment.getUserData(EXECUTION_SKIP_RUN);
    if (allowSkipRun != null && allowSkipRun) {
      environment.getProject().getMessageBus().syncPublisher(EXECUTION_TOPIC).processNotStarted(environment.getExecutor().getId(),
                                                                                                environment);
    }
    else {
      // important! Do not use DumbService.smartInvokeLater here because it depends on modality state
      // and execution of startRunnable could be skipped if modality state check fails
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (!myProject.isDisposed()) {
          RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
          if (settings != null && !settings.getType().isDumbAware() && DumbService.isDumb(myProject)) {
            DumbService.getInstance(myProject).runWhenSmart(startRunnable);
          } else {
            try {
              startRunnable.run();
            } catch (IndexNotReadyException ignored) {
              ExecutionUtil.handleExecutionError(environment, new ExecutionException("cannot start while indexing is in progress."));
            }
          }
        }
      });
    }
  }

  @Override
  public void restartRunProfile(@NotNull Project project,
                                @NotNull Executor executor,
                                @NotNull ExecutionTarget target,
                                @Nullable RunnerAndConfigurationSettings configuration,
                                @Nullable ProcessHandler processHandler) {
    ExecutionEnvironmentBuilder builder = createEnvironmentBuilder(project, executor, configuration);
    if (processHandler != null) {
      for (RunContentDescriptor descriptor : getContentManager().getAllDescriptors()) {
        if (descriptor.getProcessHandler() == processHandler) {
          builder.contentToReuse(descriptor);
          break;
        }
      }
    }
    restartRunProfile(builder.target(target).build());
  }

  @Override
  public void restartRunProfile(@NotNull final ExecutionEnvironment environment) {
    RunnerAndConfigurationSettings configuration = environment.getRunnerAndConfigurationSettings();

    List<RunContentDescriptor> runningIncompatible;
    if (configuration == null) {
      runningIncompatible = Collections.emptyList();
    }
    else {
      runningIncompatible = getIncompatibleRunningDescriptors(configuration);
    }

    RunContentDescriptor contentToReuse = environment.getContentToReuse();
    final List<RunContentDescriptor> runningOfTheSameType = new SmartList<>();
    if (configuration != null && configuration.isSingleton()) {
      runningOfTheSameType.addAll(getRunningDescriptorsOfTheSameConfigType(configuration));
    }
    else if (isProcessRunning(contentToReuse)) {
      runningOfTheSameType.add(contentToReuse);
    }

    List<RunContentDescriptor> runningToStop = ContainerUtil.concat(runningOfTheSameType, runningIncompatible);
    if (!runningToStop.isEmpty()) {
      if (configuration != null) {
        if (!runningOfTheSameType.isEmpty()
            && (runningOfTheSameType.size() > 1 || contentToReuse == null || runningOfTheSameType.get(0) != contentToReuse) &&
            !userApprovesStopForSameTypeConfigurations(environment.getProject(), configuration.getName(), runningOfTheSameType.size())) {
          return;
        }
        if (!runningIncompatible.isEmpty()
            && !userApprovesStopForIncompatibleConfigurations(myProject, configuration.getName(), runningIncompatible)) {
          return;
        }
      }

      for (RunContentDescriptor descriptor : runningToStop) {
        stopProcess(descriptor);
      }
    }

    if (myAwaitingRunProfiles.get(environment.getRunProfile()) == environment) {
      // defense from rerunning exactly the same ExecutionEnvironment
      return;
    }
    myAwaitingRunProfiles.put(environment.getRunProfile(), environment);

    awaitTermination(new Runnable() {
      @Override
      public void run() {
        if (myAwaitingRunProfiles.get(environment.getRunProfile()) != environment) {
          // a new rerun has been requested before starting this one, ignore this rerun
          return;
        }
        if ((DumbService.getInstance(myProject).isDumb() && configuration != null && !configuration.getType().isDumbAware()) ||
            ExecutorRegistry.getInstance().isStarting(environment)) {
          awaitTermination(this, 100);
          return;
        }

        for (RunContentDescriptor descriptor : runningOfTheSameType) {
          ProcessHandler processHandler = descriptor.getProcessHandler();
          if (processHandler != null && !processHandler.isProcessTerminated()) {
            awaitTermination(this, 100);
            return;
          }
        }
        myAwaitingRunProfiles.remove(environment.getRunProfile());
        start(environment);
      }
    }, 50);
  }

  private void awaitTermination(@NotNull Runnable request, long delayMillis) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(request, ModalityState.any());
    }
    else {
      myAwaitingTerminationAlarm.addRequest(request, delayMillis);
    }
  }

  @NotNull
  private List<RunContentDescriptor> getRunningDescriptorsOfTheSameConfigType(@NotNull final RunnerAndConfigurationSettings configurationAndSettings) {
    return getRunningDescriptors(runningConfigurationAndSettings -> configurationAndSettings == runningConfigurationAndSettings);
  }

  @NotNull
  private List<RunContentDescriptor> getIncompatibleRunningDescriptors(@NotNull RunnerAndConfigurationSettings configurationAndSettings) {
    final RunConfiguration configurationToCheckCompatibility = configurationAndSettings.getConfiguration();
    return getRunningDescriptors(runningConfigurationAndSettings -> {
      RunConfiguration runningConfiguration = runningConfigurationAndSettings == null ? null : runningConfigurationAndSettings.getConfiguration();
      if (runningConfiguration == null || !(runningConfiguration instanceof CompatibilityAwareRunProfile)) {
        return false;
      }
      return ((CompatibilityAwareRunProfile)runningConfiguration).mustBeStoppedToRun(configurationToCheckCompatibility);
    });
  }

  @NotNull
  public List<RunContentDescriptor> getRunningDescriptors(@NotNull Condition<RunnerAndConfigurationSettings> condition) {
    List<RunContentDescriptor> result = new SmartList<>();
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      if (trinity.getSecond() != null && condition.value(trinity.getSecond())) {
        ProcessHandler processHandler = trinity.getFirst().getProcessHandler();
        if (processHandler != null /*&& !processHandler.isProcessTerminating()*/ && !processHandler.isProcessTerminated()) {
          result.add(trinity.getFirst());
        }
      }
    }
    return result;
  }

  @NotNull
  public List<RunContentDescriptor> getDescriptors(@NotNull Condition<RunnerAndConfigurationSettings> condition) {
    List<RunContentDescriptor> result = new SmartList<>();
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      if (trinity.getSecond() != null && condition.value(trinity.getSecond())) {
        result.add(trinity.getFirst());
      }
    }
    return result;
  }

  @NotNull
  public Set<Executor> getExecutors(RunContentDescriptor descriptor) {
    Set<Executor> result = new HashSet<>();
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      if (descriptor == trinity.first) result.add(trinity.third);
    }
    return result;
  }

  @NotNull
  public Set<RunnerAndConfigurationSettings> getConfigurations(RunContentDescriptor descriptor) {
    Set<RunnerAndConfigurationSettings> result = new HashSet<>();
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      if (descriptor == trinity.first) result.add(trinity.second);
    }
    return result;
  }
}
