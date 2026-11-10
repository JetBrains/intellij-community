// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
@State(name = "ActionsLocalSummary", storages = [Storage("actionSummary.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
@Service
class ActionsLocalSummary : PersistentStateComponent<ActionsLocalSummaryState>, SimpleModificationTracker() {
  @Volatile
  private var state = ActionsLocalSummaryState()
  @Volatile
  private var totalSummary: ActionsTotalSummary = ActionsTotalSummary(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE)

  override fun getState(): ActionsLocalSummaryState = state

  override fun loadState(state: ActionsLocalSummaryState) {
    this.state = state
    this.totalSummary = calculateTotalSummary(state)
  }

  private fun calculateTotalSummary(state: ActionsLocalSummaryState): ActionsTotalSummary {
    var maxUsageCount = 0
    var maxUsageCountFromSe = 0

    var minUsageCount = Integer.MAX_VALUE
    var minUsageCountFromSe = Integer.MAX_VALUE

    for (value in state.data.values) {
      maxUsageCount = max(maxUsageCount, value.usageCount)
      minUsageCount = min(minUsageCount, value.usageCount)
      maxUsageCountFromSe = max(maxUsageCountFromSe, value.usageFromSearchEverywhere)
      minUsageCountFromSe = min(minUsageCountFromSe, value.usageFromSearchEverywhere)
    }

    return ActionsTotalSummary(maxUsageCount, minUsageCount, maxUsageCountFromSe, minUsageCountFromSe)
  }

  @Synchronized
  fun getTotalStats(): ActionsTotalSummary = ActionsTotalSummary(
    totalSummary.maxUsageCount,
    totalSummary.minUsageCount,
    totalSummary.maxUsageFromSearchEverywhere,
    totalSummary.minUsageFromSearchEverywhere
  )

  @Synchronized
  fun getActionsStats(): Map<String, ActionSummary> {
    val data = state.data
    if (data.isEmpty()) {
      return emptyMap()
    }

    val result = HashMap<String, ActionSummary>(data.size)
    for (datum in data) {
      val summary = ActionSummary()
      summary.usageCount = datum.value.usageCount
      summary.lastUsedTimestamp = datum.value.lastUsedTimestamp
      result[datum.key] = summary
    }
    return result
  }

  @Synchronized
  fun getActionStatsById(actionId: String): ActionExtendedSummary? = state.data[actionId]

  @Synchronized
  internal fun updateActionsSummary(actionId: String, place: String) {
    val isFromSearchEverywhere = place == ActionPlaces.ACTION_SEARCH
    val summary = state.data.computeIfAbsent(actionId) { ActionExtendedSummary() }

    summary.incrementUsage(isFromSearchEverywhere)
    totalSummary.updateUsage(summary, isFromSearchEverywhere)
    incModificationCount()
  }
}

@ApiStatus.Internal
@Tag("i")
class ActionExtendedSummary {
  @Attribute("c")
  @JvmField
  var usageCount = 0

  @Attribute("d")
  @JvmField
  var usageFromSearchEverywhere = 0

  @Attribute("l")
  @JvmField
  var lastUsedTimestamp = 0L

  @Attribute("k")
  @JvmField
  var lastUsedFromSearchEverywhere = 0L

  fun incrementUsage(isFromSearchEverywhere: Boolean) {
    lastUsedTimestamp = System.currentTimeMillis()
    usageCount++

    if (isFromSearchEverywhere) {
      lastUsedFromSearchEverywhere = lastUsedTimestamp
      usageFromSearchEverywhere++
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ActionExtendedSummary
    return usageCount == other.usageCount &&
           usageFromSearchEverywhere == other.usageFromSearchEverywhere &&
           lastUsedTimestamp == other.lastUsedTimestamp &&
           lastUsedFromSearchEverywhere == other.lastUsedFromSearchEverywhere
  }

  override fun hashCode(): Int {
    var result = usageCount
    result = 31 * result + usageFromSearchEverywhere
    result = 31 * result + lastUsedTimestamp.hashCode()
    result = 31 * result + lastUsedFromSearchEverywhere.hashCode()
    return result
  }
}

/**
 * Class left for compatibility with Tips of the Day service
 *
 * @see com.intellij.ide.util.TipsOrderUtil
 */
@ApiStatus.Internal
@Tag("i")
class ActionSummary {
  @Attribute("c")
  @JvmField
  var usageCount = 0

  @Attribute("l")
  @JvmField
  var lastUsedTimestamp = System.currentTimeMillis()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ActionSummary
    return usageCount == other.usageCount && lastUsedTimestamp == other.lastUsedTimestamp
  }

  override fun hashCode() = (31 * usageCount) + lastUsedTimestamp.hashCode()
}

@ApiStatus.Internal
data class ActionsLocalSummaryState(
  @get:XMap(entryTagName = "e", keyAttributeName = "n") @get:Property(surroundWithTag = false)
  internal val data: MutableMap<String, ActionExtendedSummary> = HashMap()
)

@ApiStatus.Internal
class ActionsTotalSummary(
  var maxUsageCount: Int,
  var minUsageCount: Int,
  var maxUsageFromSearchEverywhere: Int,
  var minUsageFromSearchEverywhere: Int
) {
  fun updateUsage(newUsageSummary: ActionExtendedSummary, isFromSearchEverywhere: Boolean) {
    maxUsageCount = max(newUsageSummary.usageCount, maxUsageCount)
    minUsageCount = min(newUsageSummary.usageCount, minUsageCount)

    if (isFromSearchEverywhere) {
      maxUsageFromSearchEverywhere = max(newUsageSummary.usageFromSearchEverywhere, maxUsageFromSearchEverywhere)
      minUsageFromSearchEverywhere = min(newUsageSummary.usageFromSearchEverywhere, minUsageFromSearchEverywhere)
    }
  }
}

@Service
private class ActionsLocalSummaryUpdater(coroutineScope: CoroutineScope) {
  private val events = MutableSharedFlow<ActionUsage>(replay = 1, extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      events.collect { usage ->
        try {
          if (getPluginInfo(usage.actionClass).isSafeToReport()) {
            serviceAsync<ActionsLocalSummary>().updateActionsSummary(usage.actionId ?: usage.actionClass.name, usage.place)
          }
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          thisLogger().error(e)
        }
      }
    }
  }

  fun incUsage(actionClass: Class<out AnAction>, actionId: String?, place: String) {
    events.tryEmit(ActionUsage(actionClass, actionId, place))
  }
}

private class ActionUsage(val actionClass: Class<out AnAction>, val actionId: String?, val place: String)

internal class ActionsLocalSummaryListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    service<ActionsLocalSummaryUpdater>().incUsage(action.javaClass, event.actionManager.getId(action), event.place)
  }
}