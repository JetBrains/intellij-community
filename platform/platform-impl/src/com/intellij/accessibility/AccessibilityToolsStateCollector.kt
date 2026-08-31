// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.execution.process.OSProcessUtil
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.wm.impl.LinuxUiUtil
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.NSAutoreleasePool
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.awt.Toolkit

@OptIn(LowLevelLocalMachineAccess::class)
internal class AccessibilityToolsStateCollector : ApplicationUsagesCollector() {
  private enum class ScreenReader {
    NVDA, JAWS, VoiceOver, Orca
  }

  private enum class ScreenMagnifier {
    WindowsMagnifier, MacOSZoom, GnomeMagnifier
  }

  private enum class VoiceControl {
    WindowsVoiceAccess, MacOSVoiceControl
  }

  private val GROUP = EventLogGroup("accessibility.tools.state", 2)

  private val SCREEN_READER = GROUP.registerEvent(
    "screen.reader",
    EventFields.Enum<ScreenReader>("name", "Name of the screen reader tool"))
  private val SCREEN_MAGNIFIER = GROUP.registerEvent(
    "screen.magnifier",
    EventFields.Enum<ScreenMagnifier>("name", "Name of the screen magnifier tool"))
  private val VOICE_CONTROL = GROUP.registerEvent(
    "voice.control",
    EventFields.Enum<VoiceControl>("name", "Name of the voice control tool"))
  private val OS_HIGH_CONTRAST = GROUP.registerEvent(
    "os.high.contrast",
    EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val set = mutableSetOf<MetricEvent>()

    when (OS.CURRENT) {
      OS.Windows -> {
        try {
          ProcessHandle.allProcesses().forEach {
            when (OSProcessUtil.processName(it).lowercase()) {
              "nvda.exe" -> {
                set.add(SCREEN_READER.metric(ScreenReader.NVDA))
              }
              "jfw.exe" -> {
                set.add(SCREEN_READER.metric(ScreenReader.JAWS))
              }
              "magnify.exe" -> {
                set.add(SCREEN_MAGNIFIER.metric(ScreenMagnifier.WindowsMagnifier))
              }
              "voiceaccess.exe" -> {
                set.add(VOICE_CONTROL.metric(VoiceControl.WindowsVoiceAccess))
              }
            }
          }
        }
        catch (_: Throwable) {
          // Failure to get the process list can happen but very rarely. Treat it as if there are no AT running so we can collect other data
        }

        if (Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == true) {
          set.add(OS_HIGH_CONTRAST.metric(true))
        }
      }

      OS.macOS -> {
        val pool = NSAutoreleasePool()
        var universalAccess: ID? = null
        var accessibility: ID? = null
        try {
          universalAccess = Foundation.invoke(
            Foundation.invoke("NSUserDefaults", "alloc"),
            "initWithSuiteName:",
            Foundation.nsString("com.apple.universalaccess")
          )
          if (Foundation.invoke(universalAccess, "boolForKey:", Foundation.nsString("voiceOverOnOffKey")).booleanValue()) {
            set.add(SCREEN_READER.metric(ScreenReader.VoiceOver))
          }
          if (Foundation.invoke(universalAccess, "boolForKey:", Foundation.nsString("closeViewZoomedIn")).booleanValue()) {
            set.add(SCREEN_MAGNIFIER.metric(ScreenMagnifier.MacOSZoom))
          }
          if (Foundation.invoke(universalAccess, "boolForKey:", Foundation.nsString("increaseContrast")).booleanValue()) {
            set.add(OS_HIGH_CONTRAST.metric(true))
          }

          accessibility = Foundation.invoke(
            Foundation.invoke("NSUserDefaults", "alloc"),
            "initWithSuiteName:",
            Foundation.nsString("com.apple.Accessibility")
          )
          if (Foundation.invoke(accessibility, "boolForKey:", Foundation.nsString("CommandAndControlEnabled")).booleanValue()) {
            set.add(VOICE_CONTROL.metric(VoiceControl.MacOSVoiceControl))
          }
        }
        finally {
          if (universalAccess != null) {
            Foundation.cfRelease(universalAccess)
          }
          if (accessibility != null) {
            Foundation.cfRelease(accessibility)
          }
          pool.drain()
        }
      }

      OS.Linux -> {
        if (LinuxUiUtil.isOrcaProcessRunning()) {
          set.add(SCREEN_READER.metric(ScreenReader.Orca))
        }
        if (LinuxUiUtil.isGnomeZoomEnabled()) {
          set.add(SCREEN_MAGNIFIER.metric(ScreenMagnifier.GnomeMagnifier))
        }
        if (LinuxUiUtil.isGnomeHighContrastEnabled()) {
          set.add(OS_HIGH_CONTRAST.metric(true))
        }
      }

      else -> { }
    }

    return set
  }
}
