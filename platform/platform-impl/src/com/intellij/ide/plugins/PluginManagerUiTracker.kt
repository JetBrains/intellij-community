// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.UI
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeMark
import kotlin.time.measureTimedValue

@ApiStatus.Internal
class PluginManagerUiTracker {

  private val meter by lazy { TelemetryManager.getMeter(UI) }
  private val keyToHistogram: MutableMap<PluginManagerUiMetric, LongHistogram> = ConcurrentHashMap()
  private val keyToCounter: MutableMap<PluginManagerUiEvent, LongCounter> = ConcurrentHashMap()

  fun measure(metric: PluginManagerUiMetric, timeMs: Long) {
    keyToHistogram.computeIfAbsent(metric) {
      meter.histogramBuilder(getMetricName(it.metricName))
        .setUnit("ms")
        .ofLongs()
        .build()
    }.record(timeMs)
  }

  fun measure(metric: PluginManagerUiMetric, start: TimeMark) {
    measure(metric, start.elapsedNow().inWholeMilliseconds)
  }

  fun <T> measure(metric: PluginManagerUiMetric, compute: () -> T): T {
    val (result, timeSpent) = measureTimedValue {
      compute()
    }
    measure(metric, timeSpent.inWholeMilliseconds)
    return result
  }

  fun logEvent(event: PluginManagerUiEvent) {
    keyToCounter.computeIfAbsent(event) {
      meter.counterBuilder(getMetricName(it.metricName)).build()
    }.add(1)
  }

  private fun getMetricName(name: String): String {
    return "$METRIC_NAME_PREFIX$name"
  }

  companion object {
    const val METRIC_NAME_PREFIX: String = "plugin.manager.ui."
  }
}

/**
 * The single place where the names of all Plugin Manager UI duration metrics are declared.
 *
 * [PluginManagerUiTracker] prepends [PluginManagerUiTracker.METRIC_NAME_PREFIX] to [metricName],
 * so [INSTALLED_TAB_FETCH] is reported as `plugin.manager.ui.installed.tab.fetch`.
 */
@ApiStatus.Internal
enum class PluginManagerUiMetric(val metricName: String) {
  /** Loading the Installed tab data off the EDT. */
  INSTALLED_TAB_FETCH("installed.tab.fetch"),

  /** Building and applying the Installed tab UI on the EDT. */
  INSTALLED_TAB_RENDER("installed.tab.render"),

  /** Fetch and render of the Installed tab together. */
  INSTALLED_TAB_TOTAL("installed.tab.total"),

  /** Loading the Marketplace tab data off the EDT. */
  MARKETPLACE_TAB_FETCH("marketplace.tab.fetch"),

  /** Building and applying the Marketplace tab UI on the EDT. */
  MARKETPLACE_TAB_RENDER("marketplace.tab.render"),

  /** Fetch and render of the Marketplace tab together. */
  MARKETPLACE_TAB_TOTAL("marketplace.tab.total"),

  /** Loading a plugin card in the details page. */
  PLUGIN_CARD_LOAD("plugin.card.load"),

  /** A Marketplace search request round trip. */
  SEARCH_MARKETPLACE_REQUEST("search.marketplace.request"),

  /** End-to-end latency of a Marketplace search, including result processing. */
  SEARCH_MARKETPLACE_LATENCY("search.marketplace.latency"),

  /** End-to-end latency of an Installed plugins search. */
  SEARCH_INSTALLED_LATENCY("search.installed.latency"),

  /** Downloading a plugin icon from the Marketplace. */
  ICON_LOAD_REMOTE("icon.load.remote"),

  /** Reading a plugin icon from local plugin files. */
  ICON_LOAD_LOCAL("icon.load.local"),

  /** Loading available plugin updates (local and remote hosts combined). */
  UPDATES_LOAD("updates.load"),

  /** Loading the custom repository plugin map. */
  CUSTOM_REPOSITORIES_FETCH("custom.repositories.fetch"),

  /** Applying custom repository changes end to end, including the UI update. */
  CUSTOM_REPOSITORIES_TOTAL("custom.repositories.total"),

  /** Refreshing the combined (local + remote) plugin state after a modification. */
  COMBINED_STATE_UPDATE("combined.state.update"),
}

/**
 * The single place where the names of all Plugin Manager UI counter events are declared.
 *
 * [PluginManagerUiTracker] prepends [PluginManagerUiTracker.METRIC_NAME_PREFIX] to [metricName],
 * so [INSTALLED_TAB_LOAD_ERROR] is reported as `plugin.manager.ui.installed.tab.load.error`.
 */
@ApiStatus.Internal
enum class PluginManagerUiEvent(val metricName: String) {
  /** The Installed tab failed to load its data. */
  INSTALLED_TAB_LOAD_ERROR("installed.tab.load.error"),

  /** All Marketplace tab queries failed, so the tab has no marketplace data at all. */
  MARKETPLACE_TAB_LOAD_ERROR("marketplace.tab.load.error"),

  /** Some, but not all, Marketplace tab queries failed. */
  MARKETPLACE_TAB_LOAD_PARTIAL("marketplace.tab.load.partial"),

  /** The user retried loading the Marketplace tab. */
  MARKETPLACE_TAB_RELOAD("marketplace.tab.reload"),

  /** A Marketplace search failed or timed out. */
  SEARCH_MARKETPLACE_ERROR("search.marketplace.error"),

  /** A Marketplace search succeeded but returned no results. */
  SEARCH_MARKETPLACE_EMPTY("search.marketplace.empty"),

  /** Loading updates from the local (frontend) host failed. */
  UPDATES_LOCAL_LOAD_ERROR("updates.local.load.error"),

  /** Loading updates from the remote (backend) host failed. */
  UPDATES_REMOTE_LOAD_ERROR("updates.remote.load.error"),
}
