// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.RunManager
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId

class RunDebugConfigManager(val project: Project) {
  enum class State {
    PROFILING,
    RUNNING_DEBUGGING,
    DEBUGGING_SEVERAL,
    RUNNING_SEVERAL,
    DEBUGGING_PAUSED,
    DEBUGGING,
    RUNNING,
    DEFAULT,
  }

  companion object {
    private val RUN_EXECUTOR_ID = ToolWindowId.RUN
    private val DEBUG_EXECUTOR_ID = ToolWindowId.DEBUG
    private val PROFILE_EXECUTOR_ID = "Profiler"

    fun getInstance(project: Project): RunDebugConfigManager? {
      return project.getService(RunDebugConfigManager::class.java)
    }
  }

  val runManager = RunManager.getInstance(project)
  val executionManager = ExecutionManagerImpl.getInstance(project)

  fun getState(): State {
    val running = executionManager.getRunning(listOf(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID, PROFILE_EXECUTOR_ID))
    if (running.containsKey(PROFILE_EXECUTOR_ID)) {
      return State.PROFILING
    }

    running[DEBUG_EXECUTOR_ID]?.let {
      if (running.containsKey(RUN_EXECUTOR_ID)) {
        return State.RUNNING_DEBUGGING
      }

      if (it.size > 1) {
        return State.DEBUGGING_SEVERAL
      }

      return State.DEBUGGING
    }
    running[RUN_EXECUTOR_ID]?.let {
      if (it.size > 1) {
        return State.RUNNING_SEVERAL
      }

      return State.RUNNING
    }

    return State.DEFAULT
  }

/*  private fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
    return runManager.selectedConfiguration
  }

  private fun canRun(executor: Executor, pairs: List<SettingsAndEffectiveTarget>): Boolean {
    if (pairs.isEmpty()) {
      return false
    }
    for ((configuration, target) in pairs) {
      if (configuration is CompoundRunConfiguration) {
        if (!canRun(executor, configuration.getConfigurationsWithEffectiveRunTargets())) {
          return false
        }
        continue
      }
      val runner: ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, configuration)
      if (runner == null || !ExecutionTargetManager.canRun(configuration, target)
          || executionManager.isStarting(executor.id, runner.runnerId)) {
        return false
      }
    }
    return true
  }*/


}