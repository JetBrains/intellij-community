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

package com.intellij.execution;

import com.intellij.execution.actions.RunContextAction;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author spleaner
 */
public class ExecutorRegistryImpl extends ExecutorRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ExecutorRegistryImpl");

  @NonNls public static final String RUNNERS_GROUP = "RunnerActions";
  @NonNls public static final String RUN_CONTEXT_GROUP = "RunContextGroup";

  private List<Executor> myExecutors = new ArrayList<Executor>();
  private ActionManager myActionManager;
  private final Map<String, Executor> myId2Executor = new HashMap<String, Executor>();
  private final Set<String> myContextActionIdSet = new HashSet<String>();
  private final Map<String, AnAction> myId2Action = new HashMap<String, AnAction>();
  private final Map<String, AnAction> myContextActionId2Action = new HashMap<String, AnAction>();

  // [Project, ExecutorId, RunnerId]
  private final Set<Trinity<Project, String, String>> myInProgress = new java.util.HashSet<Trinity<Project, String, String>>(); 

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

    final DefaultActionGroup group = (DefaultActionGroup) myActionManager.getAction(groupId);
    group.add(action);
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

  @NotNull
  public synchronized Executor[] getRegisteredExecutors() {
    return myExecutors.toArray(new Executor[myExecutors.size()]);
  }

  public Executor getExecutorById(final String executorId) {
    return myId2Executor.get(executorId);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ExecutorRegistyImpl";
  }

  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(final Project project) {
        final MessageBusConnection connect = project.getMessageBus().connect(project);
        connect.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionAdapter(){
          public void processStartScheduled(String executorId, ExecutionEnvironment env) {
            myInProgress.add(createExecutionId(executorId, env, project));
          }

          public void processNotStarted(String executorId, @NotNull ExecutionEnvironment env) {
            myInProgress.remove(createExecutionId(executorId, env, project));
          }

          public void processStarted(String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
            myInProgress.remove(createExecutionId(executorId, env, project));
          }
        });
      }
    });
    
    
    final Executor[] executors = Extensions.getExtensions(Executor.EXECUTOR_EXTENSION_NAME);
    for (Executor executor : executors) {
      initExecutor(executor);
    }
  }

  private static Trinity<Project, String, String> createExecutionId(String executorId, ExecutionEnvironment env, Project project) {
    final ConfigurationPerRunnerSettings settings = env.getConfigurationSettings();
    return new Trinity<Project, String, String>(project, executorId, settings != null? settings.getRunnerId() : null);
  }

  public boolean isStarting(Project project, final String executorId, final String runnerId) {
    return myInProgress.contains(new Trinity<Project, String, String>(project, executorId, runnerId));
  }
  
  public synchronized void disposeComponent() {
    if (myExecutors.size() > 0) {
      List<Executor> executors = new ArrayList<Executor>(myExecutors);
      for (Executor executor : executors) {
        deinitExecutor(executor);
      }

      myExecutors = null;
    }

    myActionManager = null;
  }

  private class ExecutorAction extends AnAction implements DumbAware {
    private final Executor myExecutor;

    private ExecutorAction(@NotNull final Executor executor) {
      super(executor.getStartActionText(), executor.getActionName(), executor.getIcon());
      myExecutor = executor;
    }

    public void update(final AnActionEvent e) {
      super.update(e);

      final Presentation presentation = e.getPresentation();
      boolean enabled = false;
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

      if (project == null || !project.isInitialized() || project.isDisposed() || DumbService.getInstance(project).isDumb()) {
        presentation.setEnabled(false);
        return;
      }

      final RunnerAndConfigurationSettings selectedConfiguration = getConfiguration(project);
      if (selectedConfiguration != null) {
        final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(myExecutor.getId(), selectedConfiguration.getConfiguration());
        enabled = runner != null && !isStarting(project, myExecutor.getId(), runner.getRunnerId());

        if (enabled) {
          presentation.setDescription(myExecutor.getDescription());
        }
      }

      String text = getTemplatePresentation().getTextWithMnemonic();

      presentation.setEnabled(enabled);
      presentation.setText(text);
    }

    @Nullable
    private RunnerAndConfigurationSettings getConfiguration(@NotNull final Project project) {
      return RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    }

    public void actionPerformed(final AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null || project.isDisposed()) {
        return;
      }
      final RunnerAndConfigurationSettings configuration = getConfiguration(project);
      if (configuration == null) {
        return;
      }
      ProgramRunnerUtil.executeConfiguration(project, configuration, myExecutor);
    }
  }
}
