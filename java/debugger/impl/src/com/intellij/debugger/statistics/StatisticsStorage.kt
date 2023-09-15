// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.statistics

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.openapi.util.Key
import java.util.concurrent.ConcurrentHashMap

private val KEY: Key<StatisticsStorage> = Key.create("DEBUGGER_STATISTICS_STORAGE")

class StatisticsStorage {
  private val data = ConcurrentHashMap<StatisticElement, TimeStats>()

  private fun append(key: StatisticElement, value: TimeStats) {
    data.merge(key, value, TimeStats::plus)
  }

  companion object {
    private fun getStorage(debugProcess: DebugProcess): StatisticsStorage {
      var storage = debugProcess.getUserData(KEY)
      if (storage == null) {
        synchronized(debugProcess) {
          storage = debugProcess.getUserData(KEY)
          if (storage == null) {
            storage = StatisticsStorage()
            debugProcess.putUserData(KEY, storage)
          }
        }
      }
      return storage!!
    }

    @JvmStatic
    @JvmOverloads
    fun add(debugProcess: DebugProcess, key: StatisticElement, timeMs: Long, hits: Int = 1) {
      getStorage(debugProcess).append(key, TimeStats(timeMs, hits))
    }

    @JvmStatic
    fun addStepping(debugProcess: DebugProcess, token: Any?, timeMs: Long) {
      if (token !is SteppingStatistic) return
      getStorage(debugProcess).append(token, TimeStats(timeMs, 1))
    }

    @JvmStatic
    @Synchronized
    fun createSteppingToken(action: SteppingAction, engine: Engine) = SteppingStatistic(action, engine)


    @JvmStatic
    @Synchronized
    fun collectAndClearData(debugProcess: DebugProcess): Map<StatisticElement, TimeStats> {
      val storage = getStorage(debugProcess)
      val result = HashMap(storage.data)
      storage.data.clear()
      return result
    }
  }
}

sealed interface StatisticElement
data class BreakpointInstallStatistic(val breakpoint: Breakpoint<*>) : StatisticElement
object BreakpointVisitStatistic : StatisticElement
data class SteppingStatistic(val action: SteppingAction, val engine: Engine) : StatisticElement

data class TimeStats(val timeMs: Long = 0, val hits: Int = 0) {
  operator fun plus(other: TimeStats) = TimeStats(timeMs + other.timeMs, hits + other.hits)
}

enum class SteppingAction {
  STEP_INTO, STEP_OUT, STEP_OVER
}

enum class Engine {
  JAVA, KOTLIN
}
