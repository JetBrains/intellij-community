// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId

class RunDebugConfigManager(val project: Project) {
  enum class State {
    RUNNING_DEBUGGING_PROFILING,
    RUNNING_PROFILING,
    DEBUGGING_PROFILING,
    RUNNING_DEBUGGING,
    PROFILING_SEVERAL,
    DEBUGGING_SEVERAL,
    RUNNING_SEVERAL,
    DEBUGGING_PAUSED,
    PROFILING,
    DEBUGGING,
    RUNNING,
    DEFAULT,
  }

  companion object {
    private const val ACTION_PREFIX = "RunDebugConfig_"

    internal const val RUN_EXECUTOR_ID = ToolWindowId.RUN
    internal const val DEBUG_EXECUTOR_ID = ToolWindowId.DEBUG
    internal const val PROFILE_EXECUTOR_ID = "Profiler"

    private val ids = listOf(RUN_EXECUTOR_ID, DEBUG_EXECUTOR_ID, PROFILE_EXECUTOR_ID)

    fun getInstance(project: Project): RunDebugConfigManager? {
      return project.getService(RunDebugConfigManager::class.java)
    }

    @JvmStatic
    fun wrapAction(executor: Executor, action: AnAction): AnAction? {
      return if(ids.contains(executor.id)) {
        return BaseExecutorActionWrapper(executor, action)
      } else null
    }

    @JvmStatic
    fun generateActionID(executor: Executor): String {
      return "${ACTION_PREFIX}_${executor.id}"
    }
  }

  val runManager = RunManager.getInstance(project)
  val executionManager = ExecutionManagerImpl.getInstance(project)

  fun getState(): State {
    val runningMap = executionManager.getRunning(ids)

    val profiling = runningMap.containsKey(PROFILE_EXECUTOR_ID)
    val running = runningMap.containsKey(RUN_EXECUTOR_ID)
    val debugging = runningMap.containsKey(DEBUG_EXECUTOR_ID)

    if(profiling && running && debugging) {
      return State.RUNNING_DEBUGGING_PROFILING
    }

    if(profiling && running && debugging) {
      return State.RUNNING_DEBUGGING_PROFILING
    }
    if(running && debugging) {
      return State.RUNNING_DEBUGGING
    }
    if(profiling && running) {
      return State.RUNNING_PROFILING
    }

    if(profiling && debugging) {
      return State.DEBUGGING_PROFILING
    }

    if(profiling) {
      runningMap[PROFILE_EXECUTOR_ID]?.let {
        return if (it.size > 1) {
          State.PROFILING_SEVERAL
        } else {
          State.PROFILING
        }
      }
    }

    if(debugging) {
      runningMap[DEBUG_EXECUTOR_ID]?.let {
        return if (it.size > 1) {
          State.DEBUGGING_SEVERAL
        } else {
          State.DEBUGGING
        }
      }
    }

    if(running) {
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