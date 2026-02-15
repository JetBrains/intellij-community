// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.ExecResult
import com.intellij.openapi.wm.impl.X11UiUtilKt
import com.intellij.openapi.wm.impl.output
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

private val DEBOUNCE_DURATION = 1.seconds

// Uses the XDG Desktop Portal spec (https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Settings.html)
private const val SETTINGS_INTERFACE = "org.freedesktop.portal.Settings"
private const val SETTINGS_MEMBER = "SettingChanged"
private val CHECK_MONITOR_CMD = arrayOf(
  "dbus-monitor",
  "--help")
private val MONITOR_CMD = arrayOf(
  "dbus-monitor",
  "--profile",
  "--session",
  "type='signal',interface='$SETTINGS_INTERFACE',member='$SETTINGS_MEMBER',arg0='org.freedesktop.appearance',arg1='color-scheme'")
private val QUERY_COLOR_SCHEME = arrayOf(
  "dbus-send",
  "--session",
  "--dest=org.freedesktop.portal.Desktop",
  "--print-reply=literal",
  "/org/freedesktop/portal/desktop",
  "org.freedesktop.portal.Settings.ReadOne",
  "string:org.freedesktop.appearance",
  "string:color-scheme")

@Suppress("OPT_IN_USAGE")
@Service
internal class DBusSettingsMonitorService(private val scope: CoroutineScope) {

  private var LOG = thisLogger()

  private val darkSchemeFlow = MutableStateFlow<Boolean?>(null)
  private val darkSchemeDebounceFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private var dbusMonitorProcess = AtomicReference<Process?>(null)

  val isServiceAllowed: Boolean
    get() = SystemInfoRt.isLinux && System.getProperty("ide.linux.color.scheme.sync.support").toBoolean()

  val darkScheme: StateFlow<Boolean?> = darkSchemeFlow.asStateFlow()

  init {
    if (isServiceAllowed) {
      scope.launch {
        darkSchemeFlow.value = calcDarkScheme()

        try {
          darkSchemeDebounceFlow.debounce(DEBOUNCE_DURATION).collect {
            darkSchemeFlow.value = calcDarkScheme()
          }
        }
        finally {
          killDbusMonitorListener()
        }
      }

      scope.launch {
        try {
          startDbusMonitorListener()
        }
        finally {
          killDbusMonitorListener()
        }
      }
    }
  }

  fun runSchemeCollector(listener: (Boolean) -> Unit) {
    if (!isServiceAllowed) {
      return
    }

    scope.launch {
      darkScheme.collect {
        listener(it ?: false)
      }
    }
  }

  private fun calcDarkScheme(): Boolean? {
    ThreadingAssertions.assertBackgroundThread()

    val output = X11UiUtilKt.exec("DBusSettingsMonitorService gets color scheme", *QUERY_COLOR_SCHEME).output() ?: return null

    val split = output.splitOutput()
    val value = split.lastOrNull()?.toIntOrNull()
    val result = when (value) {
      // No preference
      0 -> false

      // Prefer dark
      1 -> true

      // Prefer light
      2 -> false

      else -> null
    }

    if (result == null) {
      LOG.info("Cannot get color scheme: $output")
    }
    else {
      LOG.info("Obtained OS color scheme value=$value, calculated isDarkScheme=$result")
    }

    return result
  }

  private fun startDbusMonitorListener() {
    ThreadingAssertions.assertBackgroundThread()

    if (X11UiUtilKt.exec("DBusSettingsMonitorService checks dbus-monitor", *CHECK_MONITOR_CMD) !is ExecResult.Success) {
      return
    }

    try {
      dbusMonitorProcess.set(ProcessBuilder(*MONITOR_CMD).start())
      LOG.info("DBus listener started")
    }
    catch (e: Throwable) {
      LOG.info("DBus listener cannot start", e)
    }

    val process = dbusMonitorProcess.get() ?: return

    try {
      process.inputStream.bufferedReader().forEachLine { line ->
        val split = line.splitOutput()
        if (split.size > 2 && split[split.size - 2] == SETTINGS_INTERFACE && split[split.size - 1] == SETTINGS_MEMBER) {
          LOG.info("SettingChanged received: $line")
          darkSchemeDebounceFlow.tryEmit(Unit)
        }
      }

      LOG.info("DBus listener stopped: output ended unexpectedly (no errors)")
    }
    catch (e: Throwable) {
      LOG.info("DBus listener stopped", e)
    }
  }

  private fun killDbusMonitorListener() {
    val process = dbusMonitorProcess.getAndSet(null)
    process?.destroyForcibly()
  }
}

private fun String.splitOutput(): List<String> {
  return split("\\s".toPattern())
}
