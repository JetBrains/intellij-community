// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.startup

import com.intellij.execution.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder.Companion.create
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

private val LOG = logger<ProjectStartupRunner>()

private val EP_NAME = ProjectExtensionPointName<BeforeRunStartupTasks>("com.intellij.beforeRunStartupTasks")

private suspend fun beforeRunAsync(project: Project) {
  for (task in EP_NAME.getExtensions(project)) {
    runCatching {
      task.beforeRun()
    }.getOrLogException(LOG)
  }
}

internal class ProjectStartupRunner : ProjectActivity {
  override suspend fun execute(project: Project) {
    val projectStartupTaskManager = ProjectStartupTaskManager.getInstance(project)
    if (projectStartupTaskManager.isEmpty) {
      return
    }

    project.getMessageBus().simpleConnect().subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
      override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        projectStartupTaskManager.delete(settings.getUniqueID())
      }

      override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings, existingId: String?) {
        if (existingId != null) {
          projectStartupTaskManager.rename(existingId, settings)
        }
        projectStartupTaskManager.checkOnChange(settings)
      }

      override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        projectStartupTaskManager.checkOnChange(settings)
      }
    })

    coroutineScope {
      beforeRunAsync(project)
      runActivities(project, projectStartupTaskManager)
    }
  }
}

private class MyExecutor(executor: Executor, configuration: RunnerAndConfigurationSettings, private val alarm: Alarm) : Runnable {
  private val environment = create(executor, configuration).contentToReuse(null).dataContext(null).activeTarget().build()
  private val project = configuration.getConfiguration().getProject()
  private var count = ATTEMPTS
  private val name = configuration.getName()

  companion object {
    const val ATTEMPTS: Int = 10
    const val PAUSE: Long = 300
  }

  override fun run() {
    if (ExecutionManager.getInstance(project).isStarting(environment)) {
      if (count <= 0) {
        showNotification(project, ExecutionBundle.message("project.startup.runner.notification.not.started", name, ATTEMPTS))
        return
      }

      --count
      alarm.addRequest(this, PAUSE)
    }

    // reporting that the task successfully started would require changing the interface of execution subsystem, not it reports errors by itself
    LOG.info("Starting startup task '$name'")
    ProgramRunnerUtil.executeConfiguration(environment, true, true)
    // same thread always
    if (alarm.isEmpty) {
      Disposer.dispose(alarm)
    }
  }
}

private suspend fun runActivities(project: Project, projectStartupTaskManager: ProjectStartupTaskManager) {
  if (!project.isTrusted()) {
    return
  }

  val configurations = ArrayList<RunnerAndConfigurationSettings>(projectStartupTaskManager.localConfigurations)
  configurations.addAll(projectStartupTaskManager.sharedConfigurations)
  withContext(Dispatchers.EDT) {
    var pause = 0L
    val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, project)
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    for (configuration in configurations) {
      if (!canBeRun(configuration)) {
        showNotification(project,
                         ExecutionBundle.message("project.startup.runner.notification.can.not.be.started", configuration.getName()))
        return@withContext
      }

      try {
        alarm.addRequest(MyExecutor(executor, configuration, alarm), pause)
      }
      catch (e: ExecutionException) {
        showNotification(project, e.message)
      }
      pause = MyExecutor.PAUSE
    }
  }
}

private fun showNotification(project: Project, text: @Nls String?) {
  ProjectStartupTaskManager.getNotificationGroup()
    .createNotification(ExecutionBundle.message("project.startup.runner.notification", text), MessageType.ERROR).notify(project)
}

internal fun canBeRun(configuration: RunnerAndConfigurationSettings): Boolean {
  return ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configuration.getConfiguration()) != null
}
