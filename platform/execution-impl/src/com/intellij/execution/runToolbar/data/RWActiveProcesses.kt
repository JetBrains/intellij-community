// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runToolbar.RunToolbarProcess
import com.intellij.execution.runToolbar.SlotDate
import com.intellij.execution.runToolbar.getRunToolbarProcess
import com.intellij.execution.runners.ExecutionEnvironment

class RWActiveProcesses {
  internal var activeSlots = mutableListOf<SlotDate>()
  val processes = mutableMapOf<RunToolbarProcess, MutableList<ExecutionEnvironment>>()
  private var activeCount = 0

  fun getActiveCount(): Int = activeCount

  fun getText(): String? {
    return when {
      activeCount == 1 -> {
        processes.entries.firstOrNull()?.let { entry ->
          entry.value.firstOrNull()?.contentToReuse?.let {
            ExecutionBundle.message("run.toolbar.started", entry.key.name, it.displayName)
          }
        }
      }
      activeCount > 1 -> {
        processes.map { ExecutionBundle.message("run.toolbar.started", it.key.name, it.value.size) }.joinToString("  ")
      }

      else -> null
    }

  }

  internal fun updateActiveProcesses(slotsData: MutableMap<String, SlotDate>) {
    processes.clear()
    val list = slotsData.values.filter { it.environment != null }.toMutableList()
    activeSlots = list
    list.mapNotNull { it.environment }.forEach { environment ->
      environment.getRunToolbarProcess()?.let {
        processes.computeIfAbsent(it) { mutableListOf() }.add(environment)
      }
    }

    activeCount = processes.values.map { it.size }.sum()
  }

  internal fun clear() {
    activeCount = 0
    processes.clear()
  }
}
