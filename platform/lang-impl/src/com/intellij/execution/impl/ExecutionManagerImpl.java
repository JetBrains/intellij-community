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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dyoma
 */
public class ExecutionManagerImpl extends ExecutionManager implements ProjectComponent {
  private final Project myProject;

  private RunContentManagerImpl myContentManager;

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
  }

  public RunContentManager getContentManager() {
    if (myContentManager == null) {
      myContentManager = new RunContentManagerImpl(myProject);
      Disposer.register(myProject, myContentManager);
    }
    return myContentManager;
  }

  public ProcessHandler[] getRunningProcesses() {
    final List<ProcessHandler> handlers = new ArrayList<ProcessHandler>();
    RunContentDescriptor[] descriptors = ((RunContentManagerImpl)getContentManager()).getAllDescriptors();
    for (RunContentDescriptor descriptor : descriptors) {
      if (descriptor != null) {
        final ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null) {
          handlers.add(processHandler);
        }
      }
    }
    return handlers.toArray(new ProcessHandler[handlers.size()]);
  }

  public void compileAndRun(final Runnable startRunnable, final RunProfile configuration, final RunProfileState state, @Nullable final Runnable onCancelRunnable) {
    if (configuration instanceof RunConfiguration) {
      final RunConfiguration runConfiguration = (RunConfiguration)configuration;
      final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);

      final Map<BeforeRunTaskProvider<BeforeRunTask>, BeforeRunTask> activeProviders = new LinkedHashMap<BeforeRunTaskProvider<BeforeRunTask>, BeforeRunTask>();
      for (final BeforeRunTaskProvider<BeforeRunTask> provider : Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, myProject)) {
        final BeforeRunTask task = runManager.getBeforeRunTask(runConfiguration, provider.getId());
        if (task != null && task.isEnabled()) {
          activeProviders.put(provider, task);
        }
      }

      if (!activeProviders.isEmpty()) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            ConfigurationPerRunnerSettings configurationSettings = state.getConfigurationSettings();
            DataContext projectContext = SimpleDataContext.getProjectContext(myProject);

            final DataContext dataContext = configurationSettings != null ? SimpleDataContext
              .getSimpleContext(BeforeRunTaskProvider.RUNNER_ID, configurationSettings.getRunnerId(), projectContext) : projectContext;
            for (BeforeRunTaskProvider<BeforeRunTask> provider : activeProviders.keySet()) {
              if(!provider.executeTask(dataContext, runConfiguration, activeProviders.get(provider))) {
                if (onCancelRunnable != null) {
                  SwingUtilities.invokeLater(onCancelRunnable);
                }
                return;
              }
            }
            DumbService.getInstance(myProject).smartInvokeLater(startRunnable);
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
    final RunContentDescriptor reuseContent = ExecutionManager.getInstance(project).getContentManager().getReuseContent(executor, env.getContentToReuse());
    final RunProfile profile = env.getRunProfile();

    project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processStartScheduled(executor.getId(), env);

    Runnable startRunnable = new Runnable() {
      public void run() {
        boolean started = false;
        try {
          if (project.isDisposed()) return;

          project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processStarting(executor.getId(), env);

          final RunContentDescriptor descriptor = starter.execute(project, executor, state, reuseContent, env);

          if (descriptor != null) {
            ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
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
      compileAndRun(startRunnable, profile, state, new Runnable() {
        public void run() {
          project.getMessageBus().syncPublisher(EXECUTION_TOPIC).processNotStarted(executor.getId(), env);
        }
      });
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
      myProject.getMessageBus().syncPublisher(EXECUTION_TOPIC).processTerminated(myProfile, myProcessHandler);
    }

    @Override
    public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
      myProject.getMessageBus().syncPublisher(EXECUTION_TOPIC).processTerminating(myProfile, myProcessHandler);
    }
  }
}
