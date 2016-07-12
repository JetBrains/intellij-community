/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class ExecutorRegistryImpl extends ExecutorRegistry {
  private static final Logger LOG = Logger.getInstance(ExecutorRegistryImpl.class);

  @NonNls public static final String RUNNERS_GROUP = "RunnerActions";
  @NonNls public static final String RUN_CONTEXT_GROUP = "RunContextGroupInner";

  private List<Executor> myExecutors = new ArrayList<Executor>();
  private ActionManager myActionManager;
  private final Map<String, Executor> myId2Executor = new HashMap<String, Executor>();
  private final Set<String> myContextActionIdSet = new HashSet<String>();
  private final Map<String, AnAction> myId2Action = new HashMap<String, AnAction>();
  private final Map<String, AnAction> myContextActionId2Action = new HashMap<String, AnAction>();

  // [Project, ExecutorId, RunnerId]
  private final Set<Trinity<Project, String, String>> myInProgress = Collections.synchronizedSet(new java.util.HashSet<Trinity<Project, String, String>>());

  public ExecutorRegistryImpl(ActionManager actionManager) {
    myActionManager = actionManager;
  }

  synchronized void initExecutor(@NotNull final Executor executor) {
    if (myId2Executor.get(executor.getId()) != null) {
      LOG.error("Executor with id: \"" + executor.getId() + "\" was already registered!");
    }

    if (myContextActionIdSet.contains(executor.getContextActionId())) {
      LOG.error("Executor with context action id: \"" + executor.getContextActionId() + "\" was already registered!");
    }

    myExecutors.add(executor);
    myId2Executor.put(executor.getId(), executor);
    myContextActionIdSet.add(executor.getContextActionId());

    registerAction(executor.getId(), new ExecutorAction(executor), RUNNERS_GROUP, myId2Action);
    registerAction(executor.getContextActionId(), new RunContextAction(executor), RUN_CONTEXT_GROUP, myContextActionId2Action);
  }

  private void registerAction(@NotNull final String actionId, @NotNull final AnAction anAction, @NotNull final String groupId, @NotNull final Map<String, AnAction> map) {
    AnAction action = myActionManager.getAction(actionId);
    if (action == null) {
      myActionManager.registerAction(actionId, anAction);
      map.put(actionId, anAction);
      action = anAction;
    }

    ((DefaultActionGroup)myActionManager.getAction(groupId)).add(action);
  }

  synchronized void deinitExecutor(@NotNull final Executor executor) {
    myExecutors.remove(executor);
    myId2Executor.remove(executor.getId());
    myContextActionIdSet.remove(executor.getContextActionId());

    unregisterAction(executor.getId(), RUNNERS_GROUP, myId2Action);
    unregisterAction(executor.getContextActionId(), RUN_CONTEXT_GROUP, myContextActionId2Action);
  }

  private void unregisterAction(@NotNull final String actionId, @NotNull final String groupId, @NotNull final Map<String, AnAction> map) {
    final DefaultActionGroup group = (DefaultActionGroup)myActionManager.getAction(groupId);
    if (group != null) {
      group.remove(myActionManager.getAction(actionId));
      final AnAction action = map.get(actionId);
      if (action != null) {
        myActionManager.unregisterAction(actionId);
        map.remove(actionId);
      }
    }
  }

  @Override
  @NotNull
  public synchronized Executor[] getRegisteredExecutors() {
    return myExecutors.toArray(new Executor[myExecutors.size()]);
  }

  @Override
  public Executor getExecutorById(final String executorId) {
    return myId2Executor.get(executorId);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "ExecutorRegistyImpl";
  }

  @Override
  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(final Project project) {
        final MessageBusConnection connect = project.getMessageBus().connect(project);
        connect.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionAdapter(){
          @Override
          public void processStartScheduled(String executorId, ExecutionEnvironment environment) {
            myInProgress.add(createExecutionId(executorId, environment));
          }

          @Override
          public void processNotStarted(String executorId, @NotNull ExecutionEnvironment environment) {
            myInProgress.remove(createExecutionId(executorId, environment));
          }

          @Override
          public void processStarted(String executorId, @NotNull ExecutionEnvironment environment, @NotNull ProcessHandler handler) {
            myInProgress.remove(createExecutionId(executorId, environment));
          }
        });
      }

      @Override
      public void projectClosed(final Project project) {
        // perform cleanup
        synchronized (myInProgress) {
          for (Iterator<Trinity<Project, String, String>> it = myInProgress.iterator(); it.hasNext(); ) {
            final Trinity<Project, String, String> trinity = it.next();
            if (project.equals(trinity.first)) {
              it.remove();
            }
          }
        }
      }
    });


    final Executor[] executors = Extensions.getExtensions(Executor.EXECUTOR_EXTENSION_NAME);
    for (Executor executor : executors) {
      initExecutor(executor);
    }
  }

  @NotNull
  private static Trinity<Project, String, String> createExecutionId(String executorId, @NotNull ExecutionEnvironment environment) {
    return Trinity.create(environment.getProject(), executorId, environment.getRunner().getRunnerId());
  }

  @Override
  public boolean isStarting(Project project, final String executorId, final String runnerId) {
    return myInProgress.contains(Trinity.create(project, executorId, runnerId));
  }

  @Override
  public boolean isStarting(@NotNull ExecutionEnvironment environment) {
    return isStarting(environment.getProject(), environment.getExecutor().getId(), environment.getRunner().getRunnerId());
  }

  @Override
  public synchronized void disposeComponent() {
    if (!myExecutors.isEmpty()) {
      for (Executor executor : new ArrayList<Executor>(myExecutors)) {
        deinitExecutor(executor);
      }
    }
    myExecutors = null;
    myActionManager = null;
  }

  private class ExecutorAction extends AnAction implements DumbAware {
    private final Executor myExecutor;

    private ExecutorAction(@NotNull final Executor executor) {
      super(executor.getStartActionText(), executor.getDescription(), executor.getIcon());
      myExecutor = executor;
    }

    @Override
    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final Project project = e.getProject();

      if (project == null || !project.isInitialized() || project.isDisposed() ||
          (DumbService.getInstance(project).isDumb() && !Registry.is("dumb.aware.run.configurations"))) {
        presentation.setEnabled(false);
        return;
      }

      final RunnerAndConfigurationSettings selectedConfiguration = getSelectedConfiguration(project);
      boolean enabled = false;
      String text;
      final String textWithMnemonic = getTemplatePresentation().getTextWithMnemonic();
      if (selectedConfiguration != null) {
        presentation.setIcon(getInformativeIcon(project, selectedConfiguration));
        final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(myExecutor.getId(), selectedConfiguration.getConfiguration());

        ExecutionTarget target = ExecutionTargetManager.getActiveTarget(project);
        enabled = ExecutionTargetManager.canRun(selectedConfiguration, target)
                  && runner != null && !isStarting(project, myExecutor.getId(), runner.getRunnerId());

        if (enabled) {
          presentation.setDescription(myExecutor.getDescription());
        }
        text = myExecutor.getStartActionText(selectedConfiguration.getName());
      }
      else {
        text = textWithMnemonic;
      }

      presentation.setEnabled(enabled);
      presentation.setText(text);
    }

    private Icon getInformativeIcon(Project project, final RunnerAndConfigurationSettings selectedConfiguration) {
      final ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);

      RunConfiguration configuration = selectedConfiguration.getConfiguration();
      if (configuration instanceof RunnerIconProvider) {
        RunnerIconProvider provider = (RunnerIconProvider)configuration;
        Icon icon = provider.getExecutorIcon(configuration, myExecutor);
        if (icon != null) {
          return icon;
        }
      }

      List<RunContentDescriptor> runningDescriptors =
        executionManager.getRunningDescriptors(s -> s == selectedConfiguration);
      runningDescriptors = ContainerUtil.filter(runningDescriptors, descriptor -> {
        RunContentDescriptor contentDescriptor =
          executionManager.getContentManager().findContentDescriptor(myExecutor, descriptor.getProcessHandler());
        return contentDescriptor != null && executionManager.getExecutors(contentDescriptor).contains(myExecutor);
      });

      if (!runningDescriptors.isEmpty() && DefaultRunExecutor.EXECUTOR_ID.equals(myExecutor.getId()) && selectedConfiguration.isSingleton()) {
        return AllIcons.Actions.Restart;
      }
      if (runningDescriptors.isEmpty()) {
        return myExecutor.getIcon();
      }

      if (runningDescriptors.size() == 1) {
        return ExecutionUtil.getLiveIndicator(myExecutor.getIcon());
      }
      else {
        return IconUtil.addText(myExecutor.getIcon(), String.valueOf(runningDescriptors.size()));
      }
    }

    @Nullable
    private RunnerAndConfigurationSettings getSelectedConfiguration(@NotNull final Project project) {
      return RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      final Project project = e.getProject();
      if (project == null || project.isDisposed()) {
        return;
      }

      RunnerAndConfigurationSettings configuration = getSelectedConfiguration(project);
      ExecutionEnvironmentBuilder builder = configuration == null ? null : ExecutionEnvironmentBuilder.createOrNull(myExecutor, configuration);
      if (builder == null) {
        return;
      }
      ExecutionManager.getInstance(project).restartRunProfile(builder.activeTarget().dataContext(e.getDataContext()).build());
    }
  }
}
