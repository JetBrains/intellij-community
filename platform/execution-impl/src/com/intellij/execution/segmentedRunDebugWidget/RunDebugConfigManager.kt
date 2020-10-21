// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.Executor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId

internal class RunDebugConfigManager(private val project: Project) {
  enum class State(val running: Boolean = false, val debugging: Boolean = false, val profiling: Boolean = false) {
    RUNNING_DEBUGGING_PROFILING(true, true, true),
    RUNNING_PROFILING(true, profiling = true),
    DEBUGGING_PROFILING(debugging = true, profiling = true),
    RUNNING_DEBUGGING(true, true),
    PROFILING_SEVERAL(profiling = true),
    DEBUGGING_SEVERAL(debugging = true),
    RUNNING_SEVERAL(true),
    DEBUGGING_PAUSED(debugging = true),
    PROFILING(profiling = true),
    DEBUGGING(debugging = true),
    RUNNING(true),
    DEFAULT
  }

  companion object {
    private const val ACTION_PREFIX = "RunDebugConfig_"

    internal const val RUN_EXECUTOR_ID = ToolWindowId.RUN
    internal const val DEBUG_EXECUTOR_ID = ToolWindowId.DEBUG
    internal const val PROFILE_EXECUTOR_ID = "Profiler"

    private val ids = listOf(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID, PROFILE_EXECUTOR_ID)

    fun getInstance(project: Project): RunDebugConfigManager = project.service()

    @JvmStatic
    fun wrapAction(executor: Executor, action: AnAction): AnAction? {
      if (ids.contains(executor.id)) {
        return BaseExecutorActionWrapper(executor, action)
      }
      else {
        return null
      }
    }

    @JvmStatic
    fun generateActionID(executor: Executor) = "${ACTION_PREFIX}_${executor.id}"
  }

  fun getState(): State {
    val runningMap = ExecutionManagerImpl.getInstance(project).getRunning(ids)

    val profiling = runningMap.containsKey(PROFILE_EXECUTOR_ID)
    val running = runningMap.containsKey(RUN_EXECUTOR_ID)
    val debugging = runningMap.containsKey(DEBUG_EXECUTOR_ID)

    if (profiling && running && debugging) {
      return State.RUNNING_DEBUGGING_PROFILING
    }

    if (profiling && running && debugging) {
      return State.RUNNING_DEBUGGING_PROFILING
    }
    if (running && debugging) {
      return State.RUNNING_DEBUGGING
    }
    if (profiling && running) {
      return State.RUNNING_PROFILING
    }

    if (profiling && debugging) {
      return State.DEBUGGING_PROFILING
    }

    if (profiling) {
      runningMap[PROFILE_EXECUTOR_ID]?.let {
        return if (it.size > 1) {
          State.PROFILING_SEVERAL
        }
        else {
          State.PROFILING
        }
      }
    }

    if (debugging) {
      runningMap[DEBUG_EXECUTOR_ID]?.let {
        return if (it.size > 1) {
          State.DEBUGGING_SEVERAL
        }
        else {
          State.DEBUGGING
        }
      }
    }

    if (running) {
      runningMap[RUN_EXECUTOR_ID]?.let {
        return if (it.size > 1) {
          State.RUNNING_SEVERAL
        }
        else {
          State.RUNNING
        }
      }
    }

    return State.DEFAULT
  }
}