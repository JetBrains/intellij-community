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
package com.intellij.execution.startup;

import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupRunner implements StartupActivity, DumbAware {
  public static final int DELAY_MILLIS = 200;

  @Override
  public void runActivity(@NotNull final Project project) {
    final ProjectStartupTaskManager projectStartupTaskManager = ProjectStartupTaskManager.getInstance(project);
    if (projectStartupTaskManager.isEmpty()) return;

    project.getMessageBus().connect().subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        projectStartupTaskManager.delete(settings.getUniqueID());
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings, String existingId) {
        if (existingId != null) {
          projectStartupTaskManager.rename(existingId, settings);
        }
        projectStartupTaskManager.checkOnChange(settings);
      }

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        projectStartupTaskManager.checkOnChange(settings);
      }
    });
    scheduleRunActivities(project);
  }

  private static void scheduleRunActivities(@NotNull Project project) {
    JobScheduler.getScheduler().schedule(() -> {
      if (!((StartupManagerEx)StartupManager.getInstance(project)).postStartupActivityPassed()) {
        scheduleRunActivities(project);
      }
      else {
        runActivities(project);
      }
    }, DELAY_MILLIS, TimeUnit.MILLISECONDS);
  }

  private static void runActivities(final Project project) {
    final ProjectStartupTaskManager projectStartupTaskManager = ProjectStartupTaskManager.getInstance(project);
    final List<RunnerAndConfigurationSettings> configurations =
      new ArrayList<>(projectStartupTaskManager.getLocalConfigurations());
    configurations.addAll(projectStartupTaskManager.getSharedConfigurations());

    ApplicationManager.getApplication().invokeLater(() -> {
      long pause = 0;
      final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
      final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      for (final RunnerAndConfigurationSettings configuration : configurations) {
        if (! canBeRun(configuration)) {
          showNotification(project, "Run Configuration '" + configuration.getName() + "' can not be started with 'Run' action.", MessageType.ERROR);
          return;
        }

        try {
          alarm.addRequest(new MyExecutor(executor, configuration, alarm), pause);
        }
        catch (ExecutionException e) {
          showNotification(project, e.getMessage(), MessageType.ERROR);
        }
        pause = MyExecutor.PAUSE;
      }
    });
  }

  private static void showNotification(Project project, String text, MessageType type) {
    ProjectStartupTaskManager.NOTIFICATION_GROUP.createNotification(ProjectStartupTaskManager.PREFIX + " " + text, type).notify(project);
  }

  private static class MyExecutor implements Runnable {
    public static final int ATTEMPTS = 10;
    private final ExecutionEnvironment myEnvironment;
    @NotNull private final Alarm myAlarm;
    private final Project myProject;
    private int myCnt = ATTEMPTS;
    private final static long PAUSE = 300;
    private final String myName;

    public MyExecutor(@NotNull final Executor executor, @NotNull final RunnerAndConfigurationSettings configuration,
                      @NotNull Alarm alarm) throws ExecutionException {
      myName = configuration.getName();
      myProject = configuration.getConfiguration().getProject();
      myAlarm = alarm;
      myEnvironment = ExecutionEnvironmentBuilder.create(executor, configuration).contentToReuse(null).dataContext(null)
        .activeTarget().build();
    }

    @Override
    public void run() {
      if (ExecutorRegistry.getInstance().isStarting(myEnvironment)) {
        if (myCnt <= 0) {
          showNotification(myProject, "'" + myName + "' not started after " + ATTEMPTS + " attempts.", MessageType.ERROR);
          return;
        }
        --myCnt;
        myAlarm.addRequest(this, PAUSE);
      }
      // reporting that the task successfully started would require changing the interface of execution subsystem, not it reports errors by itself
      ProjectStartupTaskManager.NOTIFICATION_GROUP
        .createNotification(ProjectStartupTaskManager.PREFIX + " starting '" + myName + "'", MessageType.INFO).notify(myProject);
      ProgramRunnerUtil.executeConfiguration(myEnvironment, true, true);
      // same thread always
      if (myAlarm.isEmpty()) Disposer.dispose(myAlarm);
    }
  }

  public static boolean canBeRun(@NotNull RunnerAndConfigurationSettings configuration) {
    return RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration.getConfiguration()) != null;
  }
}
