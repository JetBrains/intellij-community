/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.startup;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerAdapter;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupRunner implements StartupActivity {
  public static final int DELAY_MILLIS = 200;

  @Override
  public void runActivity(@NotNull Project project) {
    final ProjectStartupConfiguration projectStartupConfiguration = ProjectStartupConfiguration.getInstance(project);
    if (projectStartupConfiguration.isEmpty()) return;

    RunManagerImpl.getInstanceImpl(project).addRunManagerListener(new RunManagerAdapter() {
      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        projectStartupConfiguration.delete(settings.getName());
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, String existingId) {
        projectStartupConfiguration.rename(existingId, settings);
      }
    });
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    alarm.addRequest(createRequest(project, alarm), DELAY_MILLIS);
  }

  private Runnable createRequest(final Project project, final Alarm alarm) {
    return new Runnable() {
      @Override
      public void run() {
        if (! ((StartupManagerEx) StartupManager.getInstance(project)).postStartupActivityPassed()) {
          alarm.addRequest(createRequest(project, alarm), DELAY_MILLIS);
        } else {
          runActivities(project);
        }
      }
    };
  }

  private void runActivities(final Project project) {
    final List<RunnerAndConfigurationSettings> configurations = ProjectStartupConfiguration.getInstance(project).getStartupConfigurations();
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    for (final RunnerAndConfigurationSettings configuration : configurations) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ProgramRunnerUtil.executeConfiguration(project, configuration, executor);
        }
      }, ModalityState.any());
    }
  }
}
