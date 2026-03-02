// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.ExecResult
import com.intellij.openapi.wm.impl.LinuxUiUtil
import com.intellij.openapi.wm.impl.output
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UnixDesktopEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private val UNSUPPORTED_DESKTOPS = setOf(
  UnixDesktopEnv.CINNAMON // Doesn't support DBus events during theme auto switching
)

@Service
internal class DBusSettingsMonitorService(private val scope: CoroutineScope) {

  private var LOG = thisLogger()

  @Volatile
  private var listener: ((Boolean) -> Unit)? = null
  private val darkSchemeFlow = MutableStateFlow<Boolean?>(null)
  private val darkSchemeDebounceFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)
  private var dbusMonitorProcess = AtomicReference<Process?>(null)

  val isServiceAllowed: Boolean
    get() = SystemInfoRt.isLinux && !UNSUPPORTED_DESKTOPS.contains(UnixDesktopEnv.CURRENT)

  val darkScheme: StateFlow<Boolean?> = darkSchemeFlow.asStateFlow()

  init {
    if (isServiceAllowed) {
      scope.launch {
        darkSchemeFlow.value = calcDarkScheme()

        try {
          @OptIn(FlowPreview::class)
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
    else {
      val current = UnixDesktopEnv.CURRENT
      if (current != null && UNSUPPORTED_DESKTOPS.contains(current)) {
        LOG.info("DBus is not fully supported on ${current.presentableName}. Theme synchronization will be disabled.")
      }
    }
  }

  fun setDarkSchemeListener(listener: (Boolean) -> Unit) {
    this.listener = listener
  }

  fun runSchemeCollector() {
    if (!isServiceAllowed) {
      return
    }

    scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      darkScheme.collect {
        listener?.invoke(it ?: false)
      }
    }
  }

  private fun calcDarkScheme(): Boolean? {
    ThreadingAssertions.assertBackgroundThread()

    val output = LinuxUiUtil.exec("DBusSettingsMonitorService gets color scheme", *QUERY_COLOR_SCHEME).output() ?: return null

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

  /**
   * The method starts a process and reads its output for the entire lifetime of the IDE.
   * It uses I/O operations and doesn't obey coroutine cancellation
   */
  private suspend fun startDbusMonitorListener() {
    withContext(Dispatchers.IO) {
      if (LinuxUiUtil.exec("DBusSettingsMonitorService checks dbus-monitor", *CHECK_MONITOR_CMD) !is ExecResult.Success) {
        return@withContext
      }

      try {
        dbusMonitorProcess.set(ProcessBuilder(*MONITOR_CMD).start())
        LOG.info("DBus listener started")
      }
      catch (e: Throwable) {
        LOG.info("DBus listener cannot start", e)
      }

      val process = dbusMonitorProcess.get() ?: return@withContext

      try {
        process.inputStream.bufferedReader().forEachLine { line ->
          val split = line.splitOutput()
          if (split.size > 2 && split[split.size - 2] == SETTINGS_INTERFACE && split[split.size - 1] == SETTINGS_MEMBER) {
            LOG.info("SettingChanged received: $line")
            check(darkSchemeDebounceFlow.tryEmit(Unit))
          }
        }

        LOG.info("DBus listener stopped: output ended unexpectedly (no errors)")
      }
      catch (e: Throwable) {
        LOG.info("DBus listener stopped", e)
      }
    }
  }

  private fun killDbusMonitorListener() {
    val process = dbusMonitorProcess.getAndSet(null)
    process?.destroyForcibly()
  }
}

private class DBusSettingsMonitorLifecycleListener : AppLifecycleListener {

  override fun appStarted() {
    // This code also preloads the service, so LinuxThemeDetector.detectionSupported will contain actual value when needed.
    // Example of a possible problem: IJPL-235150
    service<DBusSettingsMonitorService>().runSchemeCollector()
  }
}

private fun String.splitOutput(): List<String> {
  return split("\\s".toPattern())
}
