// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel.*
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.java.compiler.charts.CompilationChartsViewModel.Modules.EventKey
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

data class DataModel(private val charts: Charts) {
  internal val chart: ChartModel = ChartModel()
  internal lateinit var usage: UsageModel
  fun progress(init: ChartModel.() -> Unit) {
    chart.init()
  }

  fun usage(type: ChartUsage, init: UsageModel.() -> Unit) {
    charts.usage = type
    usage = type.state
    usage.init()
  }
}

class ChartModel {
  internal var model: MutableMap<EventKey, List<Modules.Event>> = mutableMapOf()
    set(value) {
      field = value
      value.values.flatten().forEach {
        threads = max(threads, it.threadNumber)
        start = min(start, it.target.time)
        end = max(end, it.target.time)
      }
    }
  internal var filter: Predicate<EventKey> = Predicate<EventKey> { _ -> true }
  internal var threads: Int = 0
  internal var start: Long = Long.MAX_VALUE
  internal var end: Long = Long.MIN_VALUE
}

class UsageModel {
  internal var model: MutableSet<StatisticData> = mutableSetOf()
    set(value) {
      field = value
      value.forEach {
        start = min(start, it.time)
        end = max(end, it.time)
      }
    }
  internal var type: CpuMemoryStatisticsType = MEMORY

  internal var start: Long = Long.MAX_VALUE
  internal var end: Long = Long.MIN_VALUE
  internal var maximum by Delegates.notNull<Long>()
}