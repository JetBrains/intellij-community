// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.configurationStore.serialize
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

private enum class DefaultBrowserKind {
  SYSTEM_DEFAULT,
  IDE_BROWSER,
  CHROME,
  FIREFOX,
  CUSTOM,
}

/** The Chromium-based browser of the JavaScript debugger. The predefined Chrome is the default, so it has no value. */
private enum class DebugBrowserKind {
  EDGE,
  OPERA,
  CUSTOM,
}

private enum class ToolbarFileKind {
  HTML,
  XML,
}

private enum class ReloadTarget {
  SERVER,
  PREVIEW,
}

/** A copy of [ReloadMode], so a change of the platform enum does not change the scheme of the group. */
private enum class ReloadModeKind {
  DISABLED,
  RELOAD_ON_SAVE,
  RELOAD_ON_CHANGE,
}

private val GROUP = EventLogGroup("web.browsers.settings", 2)

private val BROWSER = EventFields.Enum(
  name = "browser",
  enumClass = DefaultBrowserKind::class.java,
) { it.name.lowercase() }

private val DEFAULT_BROWSER = GROUP.registerEvent("default.browser", BROWSER)

private val DEBUG_BROWSER_KIND = EventFields.Enum(
  name = "browser",
  enumClass = DebugBrowserKind::class.java,
) { it.name.lowercase() }

private val DEBUG_BROWSER = GROUP.registerEvent(
  eventId = "debug.browser",
  eventField1 = DEBUG_BROWSER_KIND,
)

private val FILE_KIND = EventFields.Enum(
  name = "file_kind",
  enumClass = ToolbarFileKind::class.java,
) { it.name.lowercase() }

private val OPEN_IN_BROWSER_TOOLBAR = GROUP.registerEvent(
  eventId = "open.in.browser.toolbar",
  eventField1 = FILE_KIND,
  eventField2 = EventFields.Enabled,
)

private val RELOAD_TARGET = EventFields.Enum(
  name = "target",
  enumClass = ReloadTarget::class.java
) { it.name.lowercase() }

private val RELOAD_MODE = EventFields.Enum(
  name = "mode",
  enumClass = ReloadModeKind::class.java
) { it.name.lowercase() }

private val RELOAD = GROUP.registerEvent(
  eventId = "reload.mode",
  eventField1 = RELOAD_TARGET,
  eventField2 = RELOAD_MODE
)

private val CUSTOM_ADDED = EventFields.Boolean("custom_added")

private val PREDEFINED_DISABLED = EventFields.Boolean("predefined_disabled")

private val PREDEFINED_ENABLED = EventFields.Boolean("predefined_enabled")

private val PREDEFINED_EDITED = EventFields.Boolean("predefined_edited")

private val ORDER_CHANGED = EventFields.Boolean("order_changed")

private val INSTALLED_BROWSERS = GROUP.registerVarargEvent(
  "installed.browsers",
  CUSTOM_ADDED,
  PREDEFINED_DISABLED,
  PREDEFINED_ENABLED,
  PREDEFINED_EDITED,
  ORDER_CHANGED,
)

/**
 * Reports the settings of [WebBrowserManager]. `default.browser` comes always.
 * The other events come only when their settings differ from the defaults.
 */
internal class WebBrowserSettingsCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val manager = WebBrowserManager.getInstance()
    return buildSet {
      add(DEFAULT_BROWSER.metric(defaultBrowserKind(manager)))
      debugBrowserKind(manager)?.let { add(DEBUG_BROWSER.metric(it)) }
      if (!manager.isShowBrowserHover) {
        add(OPEN_IN_BROWSER_TOOLBAR.metric(ToolbarFileKind.HTML, false))
      }
      if (manager.isShowBrowserHoverXml) {
        add(OPEN_IN_BROWSER_TOOLBAR.metric(ToolbarFileKind.XML, true))
      }
      if (manager.webServerReloadMode != WebBrowserManager.BROWSER_RELOAD_MODE_DEFAULT) {
        add(RELOAD.metric(ReloadTarget.SERVER, reloadModeKind(manager.webServerReloadMode)))
      }
      if (manager.webPreviewReloadMode != WebBrowserManager.PREVIEW_RELOAD_MODE_DEFAULT) {
        add(RELOAD.metric(ReloadTarget.PREVIEW, reloadModeKind(manager.webPreviewReloadMode)))
      }
      // addBrowser() changes the list in place on another thread, so read a copy
      installedBrowsersChanges(manager.list.toList())?.let { add(INSTALLED_BROWSERS.metric(it)) }
    }
  }
}

private fun defaultBrowserKind(manager: WebBrowserManager): DefaultBrowserKind =
  when (manager.defaultBrowserId) {
    null -> DefaultBrowserKind.SYSTEM_DEFAULT
    WebBrowserManager.IDE_BROWSER_ID -> DefaultBrowserKind.IDE_BROWSER
    WebBrowserManager.PREDEFINED_CHROME_ID -> DefaultBrowserKind.CHROME
    WebBrowserManager.PREDEFINED_FIREFOX_ID -> DefaultBrowserKind.FIREFOX
    else -> DefaultBrowserKind.CUSTOM
  }

private fun reloadModeKind(mode: ReloadMode): ReloadModeKind =
  when (mode) {
    ReloadMode.DISABLED -> ReloadModeKind.DISABLED
    ReloadMode.RELOAD_ON_SAVE -> ReloadModeKind.RELOAD_ON_SAVE
    ReloadMode.RELOAD_ON_CHANGE -> ReloadModeKind.RELOAD_ON_CHANGE
  }

/** Returns `null` when the debugger uses the predefined Chrome, also as the fallback for a missing browser. */
private fun debugBrowserKind(manager: WebBrowserManager): DebugBrowserKind? {
  val browser = manager.chromiumBasedBrowser
  return when {
    browser.id == WebBrowserManager.PREDEFINED_CHROME_ID -> null
    WebBrowserManager.isEdge(browser) -> DebugBrowserKind.EDGE
    WebBrowserManager.isOpera(browser) -> DebugBrowserKind.OPERA
    else -> DebugBrowserKind.CUSTOM
  }
}

/**
 * Compares the browser list with the predefined browsers. Returns `null` when the list has no change.
 * The order changes when the browsers that are active by default lose their default order,
 * or when another active browser comes before one of them.
 * An added browser, and an enabled browser that is inactive by default, come at the end. They do not change the order.
 * A browser that is active by default also comes at the end when the user enables it again. Then the order changes.
 */
private fun installedBrowsersChanges(browsers: List<ConfigurableWebBrowser>): List<EventPair<*>>? {
  val predefined = WebBrowserManager.getPredefinedBrowsers().associateBy { it.id }
  val customAdded = browsers.any { it.id !in predefined }
  var disabled = false
  var enabled = false
  var edited = false
  for (browser in browsers) {
    val default = predefined[browser.id]
                  ?: continue
    if (default.isActive && !browser.isActive)
      disabled = true
    if (!default.isActive && browser.isActive)
      enabled = true
    if (browser.name != default.name || browser.path != default.path || hasChangedSettings(browser))
      edited = true
  }
  val activeIds = browsers.filter { it.isActive }.map { it.id }
  val expectedStart = predefined.values.filter { it.isActive && it.id in activeIds }.map { it.id }
  val orderChanged = activeIds.take(expectedStart.size) != expectedStart

  if (!customAdded && !disabled && !enabled && !edited && !orderChanged)
    return null

  return listOf(
    CUSTOM_ADDED.with(customAdded),
    PREDEFINED_DISABLED.with(disabled),
    PREDEFINED_ENABLED.with(enabled),
    PREDEFINED_EDITED.with(edited),
    ORDER_CHANGED.with(orderChanged),
  )
}

/** The serializer skips the default values, so it gives no element for the default settings. */
private fun hasChangedSettings(browser: ConfigurableWebBrowser): Boolean {
  val settings = browser.specificSettings
                 ?: return false
  return serialize(settings) != null
}
