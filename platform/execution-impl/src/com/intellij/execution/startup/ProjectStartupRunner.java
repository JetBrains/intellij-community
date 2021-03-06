// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.startup;

import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class ProjectStartupRunner implements StartupActivity.DumbAware {
  private static final Logger LOG = Logger.getInstance(ProjectStartupRunner.class);

  @Override
  public void runActivity(@NotNull Project project) {
    ProjectStartupTaskManager projectStartupTaskManager = ProjectStartupTaskManager.getInstance(project);
    if (projectStartupTaskManager.isEmpty()) {
      return;
    }

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

    StartupManager.getInstance(project).runAfterOpened(() -> runActivities(project));
  }

  private static void runActivities(@NotNull Project project) {
    if (!TrustedProjects.isTrusted(project)) {
      showConfirmationNotification(project);
      return;
    }

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
          showNotification(
            project,
            ExecutionBundle.message("project.startup.runner.notification.can.not.be.started", configuration.getName()));
          return;
        }

        try {
          alarm.addRequest(new MyExecutor(executor, configuration, alarm), pause);
        }
        catch (ExecutionException e) {
          showNotification(project, e.getMessage());
        }
        pause = MyExecutor.PAUSE;
      }
    }, project.getDisposed());
  }

  private static void showConfirmationNotification(@NotNull Project project) {
    Notification notification = ProjectStartupTaskManager.NOTIFICATION_GROUP.createNotification(
      ExecutionBundle.message("startup.tasks.confirmation.notification.text"),
      NotificationType.INFORMATION);
    notification.addAction(NotificationAction.createSimpleExpiring(
      ExecutionBundle.message("startup.tasks.confirmation.notification.action.allow"), () -> {
        TrustedProjects.setTrusted(project, true);
        runActivities(project);
      }));
    notification.addAction(NotificationAction.createSimpleExpiring(
      ExecutionBundle.message("startup.tasks.confirmation.notification.action.disallow"), () -> {
        TrustedProjects.setTrusted(project, false);
      }));
    notification.addAction(NotificationAction.createSimple(
      ExecutionBundle.message("startup.tasks.confirmation.notification.action.review"), () -> {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ProjectStartupConfigurable.class);
      }));
    notification.notify(project);
  }

  private static void showNotification(Project project, @Nls String text) {
    ProjectStartupTaskManager.NOTIFICATION_GROUP.createNotification(
      ExecutionBundle.message("project.startup.runner.notification", text), MessageType.ERROR).notify(project);
  }

  private static class MyExecutor implements Runnable {
    public static final int ATTEMPTS = 10;
    private final ExecutionEnvironment myEnvironment;
    @NotNull private final Alarm myAlarm;
    private final Project myProject;
    private int myCnt = ATTEMPTS;
    private final static long PAUSE = 300;
    private final String myName;

    MyExecutor(@NotNull final Executor executor, @NotNull final RunnerAndConfigurationSettings configuration,
                      @NotNull Alarm alarm) throws ExecutionException {
      myName = configuration.getName();
      myProject = configuration.getConfiguration().getProject();
      myAlarm = alarm;
      myEnvironment = ExecutionEnvironmentBuilder.create(executor, configuration).contentToReuse(null).dataContext(null)
        .activeTarget().build();
    }

    @Override
    public void run() {
      if (ExecutionManager.getInstance(myProject).isStarting(myEnvironment)) {
        if (myCnt <= 0) {
          showNotification(
            myProject,
            ExecutionBundle.message("project.startup.runner.notification.not.started", myName, ATTEMPTS));
          return;
        }
        --myCnt;
        myAlarm.addRequest(this, PAUSE);
      }
      // reporting that the task successfully started would require changing the interface of execution subsystem, not it reports errors by itself
      LOG.info("Starting startup task '" + myName + "'");
      ProgramRunnerUtil.executeConfiguration(myEnvironment, true, true);
      // same thread always
      if (myAlarm.isEmpty()) Disposer.dispose(myAlarm);
    }
  }

  public static boolean canBeRun(@NotNull RunnerAndConfigurationSettings configuration) {
    return ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration.getConfiguration()) != null;
  }
}
