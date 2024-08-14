// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts

import com.intellij.java.compiler.charts.CompilationChartsViewModel.Modules.EventKey
import com.jetbrains.rd.framework.impl.RdList
import com.jetbrains.rd.framework.impl.RdMap
import com.jetbrains.rd.framework.impl.RdProperty
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IViewableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

class CompilationChartsViewModel(val lifetime: Lifetime) {
  val modules: Modules = Modules(Long.MAX_VALUE, 0, 0, RdMap())
  val statistics: Statistics = Statistics()
  val cpuMemory: RdProperty<CpuMemoryStatisticsType> = RdProperty(CpuMemoryStatisticsType.MEMORY)
  val filter: RdProperty<Filter> = RdProperty(Filter())
  val scrollToEndEvent: RdProperty<ScrollToEndEvent> = RdProperty(ScrollToEndEvent())

  private val threadIndexes: MutableMap<Long, Int> = ConcurrentHashMap()

  fun requestScrollToEnd() = scrollToEndEvent.set(ScrollToEndEvent())

  fun started(values: List<StartTarget>) {
    values.forEach { value ->
      modules.add(Modules.StartEvent(value, index(value.thread)))
    }
  }

  fun finished(values: List<FinishTarget>) {
    values.forEach { value ->
      modules.add(Modules.FinishEvent(value, index(value.thread)))
    }
  }

  private fun index(threadId: Long): Int {
    val index: Int? = threadIndexes[threadId]
    if (index != null) return index
    synchronized(threadIndexes) {
      val updateAndGetIndex = { threadId: Long ->
        threadIndexes.size.also { newIndex -> threadIndexes[threadId] = newIndex }
      }
      return threadIndexes[threadId] ?: updateAndGetIndex(threadId)
    }
  }

  fun statistic(value: CpuMemoryStatistics) {
    statistics.maxMemory = listOf(statistics.maxMemory, value.heapUsed, value.heapMax).max()
    if (statistics.start > value.time) statistics.start = value.time
    if (statistics.end < value.time) statistics.end = value.time

    statistics.cpu.add(StatisticData(value.time, value.cpu))
    statistics.memoryMax.add(StatisticData(value.time, value.heapMax))
    statistics.memoryUsed.add(StatisticData(value.time, value.heapUsed))
  }

  data class Modules(var start: Long, var end: Long, var threadCount: Int, private val events: RdMap<EventKey, PersistentList<Event>>) {
    fun add(event: Event) {
      if (start > event.target.time) start = event.target.time
      if (end < event.target.time) end = event.target.time
      if (threadCount <= event.threadNumber) threadCount = event.threadNumber + 1

      events.compute(event.key) { _, list ->
        list?.add(event) ?: persistentListOf(event)
      }
    }

    fun get(): IViewableMap<EventKey, List<Event>> = events

    interface Event {
      val target: TargetEvent
      val threadNumber: Int

      val key: EventKey
        get() = EventKey(target.name, target.type, target.isTest)
    }

    data class EventKey(val name: String, val type: String, val test: Boolean)
    data class StartEvent(override val target: StartTarget, override val threadNumber: Int) : Event
    data class FinishEvent(override val target: FinishTarget, override val threadNumber: Int) : Event
  }

  data class StatisticData(val time: Long, val data: Long) : Comparable<StatisticData> {
    override fun compareTo(other: StatisticData): Int = time.compareTo(other.time)
  }

  data class Statistics(val memoryUsed: RdList<StatisticData> = RdList(),
                        val memoryMax: RdList<StatisticData> = RdList(),
                        val cpu: RdList<StatisticData> = RdList(),
                        var maxMemory: Long = 0,
                        var start: Long = Long.MAX_VALUE,
                        var end: Long = 0)

  data class ViewModules(var filter: Predicate<EventKey> = Predicate<EventKey> { _ -> true },
                         val data: MutableMap<EventKey, List<Modules.Event>> = ConcurrentHashMap()) {
    fun data(): Map<EventKey, List<Modules.Event>> = data(filter)
    fun data(filter: Predicate<EventKey>): Map<EventKey, List<Modules.Event>> = data.filter { filter.test(it.key) }
  }

  data class Filter(val text: List<String> = listOf(), val production: Boolean = true, val test: Boolean = true) : Predicate<EventKey> {
    fun setText(text: List<String>): Filter = Filter(text, production, test)
    fun setProduction(production: Boolean): Filter = Filter(text, production, test)
    fun setTest(test: Boolean): Filter = Filter(text, production, test)

    override fun test(key: EventKey): Boolean {
      if (text.isNotEmpty()) {
        if (!text.all { key.name.contains(it) }) return false
      }

      if (key.test) {
        if (!test) return false
      }
      else {
        if (!production) return false
      }
      return true
    }
  }

  class ScrollToEndEvent

  enum class CpuMemoryStatisticsType {
    CPU {
      override fun max(statistics: Statistics): Long = 100
    },
    MEMORY {
      override fun max(statistics: Statistics): Long = statistics.maxMemory
    };

    abstract fun max(statistics: Statistics): Long
  }
}