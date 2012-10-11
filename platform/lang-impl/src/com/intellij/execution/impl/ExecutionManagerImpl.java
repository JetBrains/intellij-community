/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author dyoma
 */
public class ExecutionManagerImpl extends ExecutionManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("com.intellij.execution.impl.ExecutionManagerImpl");

  private final Project myProject;

  private RunContentManagerImpl myContentManager;
  private final Alarm awaitingTerminationAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final List<Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor>> myRunningConfigurations =
    new CopyOnWriteArrayList<Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor>>();

  /**
   * reflection
   */
  ExecutionManagerImpl(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    ((RunContentManagerImpl)getContentManager()).init();
  }

  public void projectClosed() {
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      Disposer.dispose(trinity.first);
    }
    myRunningConfigurations.clear();
  }

  public RunContentManager getContentManager() {
    if (myContentManager == null) {
      myContentManager = new RunContentManagerImpl(myProject, DockManager.getInstance(myProject));
      Disposer.register(myProject, myContentManager);
    }
    return myContentManager;
  }

  public ProcessHandler[] getRunningProcesses() {
    final List<ProcessHandler> handlers = new ArrayList<ProcessHandler>();
    for (RunContentDescriptor descriptor : getContentManager().getAllDescriptors()) {
      final ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null) {
        handlers.add(processHandler);
      }
    }
    return handlers.toArray(new ProcessHandler[handlers.size()]);
  }

  public void compileAndRun(@NotNull final Runnable startRunnable,
                            @NotNull final ExecutionEnvironment env,
                            final @Nullable RunProfileState state,
                            @Nullable final Runnable onCancelRunnable) {
    RunProfile profile = env.getRunProfile();

    if (profile instanceof RunConfiguration) {
      final RunConfiguration runConfiguration = (RunConfiguration)profile;
      final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);

      final List<BeforeRunTask> activeTasks = new ArrayList<BeforeRunTask>();
      activeTasks.addAll(runManager.getBeforeRunTasks(runConfiguration));

      ConfigurationPerRunnerSettings configurationSettings = state != null ? state.getConfigurationSettings() : null;
      final DataContext projectContext = SimpleDataContext.getProjectContext(myProject);
      final DataContext dataContext = configurationSettings != null ? SimpleDataContext
        .getSimpleContext(BeforeRunTaskProvider.RUNNER_ID, configurationSettings.getRunnerId(), projectContext) : projectContext;

      if (!activeTasks.isEmpty()) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          /** @noinspection SSBasedInspection*/
          public void run() {
            for (BeforeRunTask task : activeTasks) {
              BeforeRunTaskProvider<BeforeRunTask> provider = BeforeRunTaskProvider.getProvider(myProject, task.getProviderId());
              if (provider == null) {
                LOG.warn("Cannot find BeforeRunTaskProvider for id='" + task.getProviderId()+"'");
                continue;
              }
              ExecutionEnvironment taskEnvironment = new ExecutionEnvironment(env.getRunProfile(),
                                                                              env.getExecutionTarget(),
                                                                              env.getProject(),
                                                                              env.getRunnerSettings(),
                                                                              env.getConfigurationSettings(),
                                                                              null,
                                                                              env.getRunnerAndConfigurationSettings());
              taskEnvironment.putUserData(RunContentDescriptor.REUSE_CONTENT_PROHIBITED, RunConfigurationBeforeRunProvider.ID.equals(provider.getId()));
              if (!provider.executeTask(dataContext, runConfiguration, taskEnvironment, task)) {
                if (onCancelRunnable != null) {
                  SwingUtilities.invokeLater(onCancelRunnable);
                }
                return;
              }
            }
            // important! Do not use DumbService.smartInvokelater here because it depends on modality state
            // and execution of startRunnable could be skipped if modality state check fails
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!myProject.isDisposed()) {
                  DumbService.getInstance(myProject).runWhenSmart(startRunnable);
                }
              }
            });
          }
        });
      }
      else {
        startRunnable.run();
      }
    }
    else {
      startRunnable.run();
    }
  }

  @Override
  public void startRunProfile(@NotNull final RunProfileStarter starter, @NotNull final RunProfileState state,
                              @NotNull final Project project, @NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    final RunContentDescriptor reuseContent =
      ExecutionManager.getInstance(project).getContentManager().getReuseContent(executor, env.getContentToReuse());
    final RunProfile profile = env.getRunProfile();

    project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processStartScheduled(executor.getId(), env);

    Runnable startRunnable = new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        boolean started = false;
        try {
          project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processStarting(executor.getId(), env);

          final RunContentDescriptor descriptor = starter.execute(project, executor, state, reuseContent, env);

          if (descriptor != null) {
            final Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity =
              Trinity.create(descriptor, env.getRunnerAndConfigurationSettings(), executor);
            myRunningConfigurations.add(trinity);
            Disposer.register(descriptor, new Disposable() {
              @Override
              public void dispose() {
                myRunningConfigurations.remove(trinity);
              }
            });
            ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor, reuseContent);
            final ProcessHandler processHandler = descriptor.getProcessHandler();
            if (processHandler != null) {
              processHandler.startNotify();
              project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processStarted(executor.getId(), env, processHandler);
              started = true;
              processHandler.addProcessListener(new ProcessExecutionListener(project, profile, processHandler));
            }
          }
        }
        catch (ExecutionException e) {
          ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), profile, e);
          LOG.info(e);
        }
        finally {
          if (!started) {
            project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processNotStarted(executor.getId(), env);
          }
        }
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      startRunnable.run();
    }
    else {
      compileAndRun(startRunnable, env, state, new Runnable() {
        public void run() {
          if (!project.isDisposed()) {
            project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processNotStarted(executor.getId(), env);
          }
        }
      });
    }
  }

  @Override
  public void restartRunProfile(@NotNull final Project project,
                                @NotNull final Executor executor,
                                @NotNull final ExecutionTarget target,
                                @NotNull final RunnerAndConfigurationSettings configuration,
                                @Nullable final ProcessHandler handler) {
    if (ProgramRunnerUtil.getRunner(executor.getId(), configuration) == null)
      return;
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    final RunManagerConfig config = runManager.getConfig();
    final List<ProcessHandler> allHandlers = new ArrayList<ProcessHandler>();
    final List<ProcessHandler> handlersToStop = new ArrayList<ProcessHandler>();
    final List<Pair<RunContentDescriptor, Executor>> pairs = getRunningDescriptors(configuration);
    if (configuration.isSingleton()) {
      for (Pair<RunContentDescriptor, Executor> pair : pairs) {
        allHandlers.add(pair.getFirst().getProcessHandler());
      }
    }
    else if (handler != null) {
      allHandlers.add(handler);
    }

    if (!allHandlers.isEmpty()) {
      for (Pair<RunContentDescriptor, Executor> pair : pairs) {
        ProcessHandler processHandler = pair.getFirst().getProcessHandler();
        if (processHandler == null || !allHandlers.contains(processHandler)) {
          continue;
        }
        if (!processHandler.isProcessTerminated()) {
          if (config.isRestartRequiresConfirmation()) {
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

              @Override
              public String getDoNotShowMessage() {
                return CommonBundle.message("dialog.options.do.not.show");
              }
            };
            String message;
            if (allHandlers.size() == 1 && handler != null) {//we restart single running configuration
              message = ExecutionBundle.message("rerun.confirmation.message", configuration.getName());
            }
            else {
              message = ExecutionBundle.message("rerun.singleton.confirmation.message", configuration.getName());
            }
            if (Messages.OK != Messages.showOkCancelDialog(message,
                                                           ExecutionBundle.message("rerun.confirmation.title") +
                                                           " (" +
                                                           pair.getSecond().getId() +
                                                           ")",
                                                           CommonBundle.message("button.ok"),
                                                           CommonBundle.message("button.cancel"),
                                                           Messages.getQuestionIcon(), option)) {
              return;
            }
          }
          handlersToStop.add(processHandler);
          stop(pair.getFirst());
        }
      }
    }

    if (handlersToStop.isEmpty()) {
      ProgramRunnerUtil.executeConfiguration(project, configuration, executor, target);
      return;
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        for (ProcessHandler processHandler : handlersToStop) {
          if (!processHandler.isProcessTerminated()) {
            awaitingTerminationAlarm.addRequest(this, 100);
            return;
          }
        }
        ProgramRunnerUtil.executeConfiguration(project, configuration, executor, target);
      }
    };
    awaitingTerminationAlarm.addRequest(runnable, 100);
  }

  private void forgetRunContentDescriptor(RunContentDescriptor runContentDescriptor) {
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      if (trinity.getFirst() == runContentDescriptor) {
        Disposer.dispose(runContentDescriptor);
        return;
      }
    }
  }

  private List<Pair<RunContentDescriptor, Executor>> getRunningDescriptors(RunnerAndConfigurationSettings configuration) {
    List<Pair<RunContentDescriptor, Executor>> result = new ArrayList<Pair<RunContentDescriptor, Executor>>();
    for (Trinity<RunContentDescriptor, RunnerAndConfigurationSettings, Executor> trinity : myRunningConfigurations) {
      if (trinity.getSecond() == configuration) {
        result.add(Pair.create(trinity.getFirst(), trinity.getThird()));
      }
    }
    return result;
  }


  private void stop(@NotNull RunContentDescriptor runContentDescriptor) {
    ProcessHandler processHandler = runContentDescriptor.getProcessHandler();
    if (processHandler == null)
      return;
    try {
      if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating()) {
        ((KillableProcess)processHandler).killProcess();
        return;
      }

      if (processHandler.detachIsDefault()) {
        processHandler.detachProcess();
      }
      else {
        processHandler.destroyProcess();
      }
    }
    finally {
      forgetRunContentDescriptor(runContentDescriptor);
    }
  }

  @NotNull
  public String getComponentName() {
    return "ExecutionManager";
  }

  private static class ProcessExecutionListener extends ProcessAdapter {
    private final Project myProject;
    private final RunProfile myProfile;
    private final ProcessHandler myProcessHandler;

    public ProcessExecutionListener(Project project, RunProfile profile, ProcessHandler processHandler) {
      myProject = project;
      myProfile = profile;
      myProcessHandler = processHandler;
    }

    @Override
    public void processTerminated(ProcessEvent event) {
      if (myProject.isDisposed()) return;

      myProject.getMessageBus().syncPublisher(EXECUTION_TOPIC).processTerminated(myProfile, myProcessHandler);
      VirtualFileManager.getInstance().refresh(true);
    }

    @Override
    public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
      if (myProject.isDisposed()) return;

      myProject.getMessageBus().syncPublisher(EXECUTION_TOPIC).processTerminating(myProfile, myProcessHandler);
    }
  }
}
