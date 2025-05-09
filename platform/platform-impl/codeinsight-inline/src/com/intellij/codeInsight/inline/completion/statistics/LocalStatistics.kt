// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.statistics

import com.intellij.codeInsight.inline.completion.statistics.LocalStatistics.Companion.MONTHS_LIMIT
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.openapi.components.*
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service for aggregating and persisting local statistics.
 * Statistics are retained for a limited time period defined by [MONTHS_LIMIT].
 */
@Service
@State(
  name = "DailyLocalStatistics",
  storages = [Storage("dailyLocalStatistics.xml")]
)
@ApiStatus.Internal
class LocalStatistics : SimplePersistentStateComponent<LocalStatistics.State>(State()) {

  /**
   * Registry for fields that should be tracked in statistics.
   */
  @VisibleForTesting
  object Schema {
    val registered: MutableSet<String> = ContainerUtil.newConcurrentSet<String>()

    @VisibleForTesting
    fun register(field: EventField<*>) {
      registered.add(field.name)
    }
  }

  data class Date(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
  ) : Comparable<Date> {

    internal fun serializeAsString(): String = "$year-$month-$day-$hour"

    fun minusMonths(months: Long): Date {
      val localDate = toLocalDate().minusMonths(months)
      return of(localDate, hour)
    }

    override fun compareTo(other: Date): Int {
      return compareValuesBy(this, other, Date::year, Date::month, Date::day, Date::hour)
    }

    fun toLocalDate(): LocalDate = LocalDate.of(year, month, day)

    companion object {
      internal fun parseFromString(dateString: String): Date {
        val (year, month, day, hour) = dateString.split('-')
        return Date(year.toInt(), month.toInt(), day.toInt(), hour.toInt())
      }

      fun of(localDate: LocalDate, hour: Int): Date {
        return Date(localDate.year, localDate.monthValue, localDate.dayOfMonth, hour)
      }

      fun of(localDateTime: LocalDateTime): Date {
        return Date(localDateTime.year, localDateTime.monthValue, localDateTime.dayOfMonth, localDateTime.hour)
      }

      fun now(): Date {
        return of(LocalDateTime.now())
      }
    }
  }

  class MetricValue : BaseState() {
    var count: Int by property(0)
    var sum: Float by property(0.0f)

    @get:XMap
    val distribution: MutableMap<String, Int> by map()
  }

  class DateState : BaseState() {
    @get:XMap
    val values: MutableMap<String, MetricValue> by map()
  }

  @VisibleForTesting
  class State : BaseState() {
    @get:XMap
    val dateToStats: MutableMap<String, DateState> by map()

    operator fun get(date: Date): DateState? = dateToStats[date.serializeAsString()]

    fun getOrPutValue(date: Date, name: String): MetricValue {
      return dateToStats.getOrPut(date.serializeAsString()) { DateState() }.values.getOrPut(name) { MetricValue() }
    }

    fun remove(date: Date) {
      dateToStats.remove(date.serializeAsString())
      incrementModificationCount()
    }

    fun valueModificationPerformed() {
      incrementModificationCount()
    }
  }

  private val currentDate: Date
    get() = mockedCurrentDate ?: Date.now()

  private var mockedCurrentDate: Date? = null

  @TestOnly
  fun withMockedCurrentDate(date: Date, block: LocalStatistics.() -> Unit) {
    try {
      mockedCurrentDate = date
      this.block()
    }
    finally {
      mockedCurrentDate = null
    }
  }

  @TestOnly
  val forcePrune: AtomicBoolean = AtomicBoolean(false)

  @RequiresBackgroundThread
  fun saveIfRegistered(pair: EventPair<*>) {
    if (pair.field.name !in Schema.registered) {
      return
    }
    if (forcePrune.get() || Math.random() < PRUNE_PROBABILITY) {
      prune()
    }
    val value = state.getOrPutValue(currentDate, pair.field.name)
    value.count++
    when (pair.data) {
      is Number -> {
        value.sum += (pair.data as Number).toFloat()
      }
      is Boolean, is Enum<*>, is Language -> {
        val strValue = pair.data.toString()
        value.distribution[strValue] = value.distribution.getOrDefault(strValue, 0) + 1
      }
    }
    state.valueModificationPerformed()
  }

  /**
   * Prunes data older than [MONTHS_LIMIT].
   */
  private fun prune() {
    // Prune old data
    val currentLocalDate = currentDate.toLocalDate()
    val cutoffLocalDate = LocalDate.of(currentLocalDate.year, currentLocalDate.month, 1)
      .minusMonths(MONTHS_LIMIT - 1)
    state.dateToStats.keys
      .map { Date.parseFromString(it) }
      .filter { date ->
        date.toLocalDate().isBefore(cutoffLocalDate)
      }.forEach {
        state.remove(it)
      }
  }

  /**
   * Generates JSON representation of daily statistics for all dates.
   */
  @RequiresBackgroundThread
  fun generateRepresentation(): String {
    prune()

    val jsonBuilder = StringBuilder()
    jsonBuilder.append("{\n")

    val sortedDates = state.dateToStats.entries.sortedBy { it.key }
    for ((dateIndex, dateEntry) in sortedDates.withIndex()) {
      val date = dateEntry.key
      val stats = dateEntry.value

      jsonBuilder.append("  \"$date\": {\n")

      for ((statIndex, statEntry) in stats.values.entries.withIndex()) {
        val fieldName = statEntry.key
        val value = statEntry.value

        jsonBuilder.append("    \"$fieldName\": {\n")
        jsonBuilder.append("      \"count\": ${value.count},\n")
        jsonBuilder.append("      \"sum\": ${value.sum}")

        if (value.distribution.isNotEmpty()) {
          jsonBuilder.append(",\n      \"distribution\": {\n")

          val distributions = value.distribution.entries.toList()
          for ((distIndex, distEntry) in distributions.withIndex()) {
            val distValue = distEntry.key
            val distCount = distEntry.value

            jsonBuilder.append("        \"$distValue\": $distCount")
            if (distIndex < distributions.size - 1) {
              jsonBuilder.append(",")
            }
            jsonBuilder.append("\n")
          }

          jsonBuilder.append("      }")
        }

        jsonBuilder.append("\n    }")
        if (statIndex < stats.values.size - 1) {
          jsonBuilder.append(",")
        }
        jsonBuilder.append("\n")
      }

      jsonBuilder.append("  }")
      if (dateIndex < sortedDates.size - 1) {
        jsonBuilder.append(",")
      }
      jsonBuilder.append("\n")
    }

    jsonBuilder.append("}")
    return jsonBuilder.toString()
  }

  companion object {
    fun getInstance(): LocalStatistics = service()

    /**
     * We store data for the current month and for `[MONTHS_LIMIT] - 1` previous months.
     */
    private const val MONTHS_LIMIT = 3L
    private const val PRUNE_PROBABILITY = 0.01
  }
}
