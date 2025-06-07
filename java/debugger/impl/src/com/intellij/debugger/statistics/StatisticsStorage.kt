// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.statistics

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.SteppingAction
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.Key
import java.util.concurrent.ConcurrentHashMap

private val KEY: Key<StatisticsStorage> = Key.create("DEBUGGER_STATISTICS_STORAGE")

class StatisticsStorage {
  private val data = ConcurrentHashMap<StatisticElement, Long>()
  private val timeBucketCounts = IntArray(DebuggerStatistics.bucketUpperLimits.size)

  private fun append(key: StatisticElement, timeMs: Long) = data.merge(key, timeMs, Long::plus)
  private fun remove(key: StatisticElement) = data.remove(key)

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
    fun addBreakpointInstall(debugProcess: DebugProcess, breakpoint: Breakpoint<*>, timeMs: Long) {
      getStorage(debugProcess).append(BreakpointInstallStatistic(breakpoint), timeMs)
    }

    @JvmStatic
    fun addStepping(debugProcess: DebugProcess, token: Any?, timeMs: Long) {
      if (token !is SteppingStatistic) return
      getStorage(debugProcess).append(token, timeMs)
    }

    @JvmStatic
    fun stepRequestCompleted(debugProcess: DebugProcess, token: Any?) {
      if (token !is SteppingStatistic) return
      val timeMs = getStorage(debugProcess).remove(token) ?: return
      DebuggerStatistics.logSteppingOverhead(debugProcess.project, token, timeMs)
    }

    @JvmStatic
    fun createSteppingToken(action: SteppingAction, engine: Engine): Any = SteppingStatistic(action, engine)

    @JvmStatic
    @Synchronized
    fun collectAndClearData(debugProcess: DebugProcess): Map<StatisticElement, Long> {
      val storage = getStorage(debugProcess)
      val result = HashMap(storage.data)
      storage.data.clear()
      return result
    }

    @JvmStatic
    fun getSteppingStatisticOrNull(token: Any?): SteppingStatistic? = token as? SteppingStatistic

    @JvmStatic
    fun addCommandTime(debugProcess: DebugProcess, timeMs: Long) {
      val storage = getStorage(debugProcess)
      val bucketIndex = DebuggerStatistics.bucketUpperLimits.indexOfFirst { timeMs <= it }
      if (bucketIndex < 0) {
        fileLogger().error("Unexpected command time $timeMs, found no bucket for it: ${DebuggerStatistics.bucketUpperLimits}")
        return
      }
      storage.timeBucketCounts[bucketIndex]++
    }

    @Synchronized
    internal fun collectCommandsPerformance(debugProcess: DebugProcess): IntArray {
      val storage = getStorage(debugProcess)
      return storage.timeBucketCounts.copyOf().also {
        storage.timeBucketCounts.fill(0)
      }
    }
  }
}

sealed interface StatisticElement
data class BreakpointInstallStatistic(val breakpoint: Breakpoint<*>) : StatisticElement

/**
 * Do not override equals/hashCode here to make sure that different stepping requests are collected separately.
 */
class SteppingStatistic(val action: SteppingAction, val engine: Engine) : StatisticElement

enum class Engine {
  JAVA, KOTLIN
}
