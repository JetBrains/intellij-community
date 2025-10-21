// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.execution.process.OSProcessUtil
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.NSAutoreleasePool
import com.intellij.ui.mac.foundation.ID
import java.awt.Toolkit

internal class AccessibilityToolsStateCollector : ApplicationUsagesCollector() {
  private enum class ScreenReader {
    NVDA, JAWS, VoiceOver, Orca
  }

  private enum class ScreenMagnifier {
    WindowsMagnifier, MacOSZoom
  }

  private enum class VoiceControl {
    WindowsVoiceAccess, MacOSVoiceControl
  }

  private val GROUP = EventLogGroup("accessibility.tools.state", 1, "FUS",
                                    "Statistics about the usage of third-party accessibility tools and settings")

  private val SCREEN_READER = GROUP.registerEvent(
    "screen.reader",
    EventFields.Enum<ScreenReader>("name", "Name of the screen reader tool"),
    "Screen reader is running in the system")
  private val SCREEN_MAGNIFIER = GROUP.registerEvent(
    "screen.magnifier",
    EventFields.Enum<ScreenMagnifier>("name", "Name of the screen magnifier tool"),
    "Screen magnifier is running in the system")
  private val VOICE_CONTROL = GROUP.registerEvent(
    "voice.control",
    EventFields.Enum<VoiceControl>("name", "Name of the voice control tool"),
    "Voice control is running in the system")
  private val OS_HIGH_CONTRAST = GROUP.registerEvent(
    "os.high.contrast",
    EventFields.Enabled,
    "High Contrast mode is enabled in the OS settings")

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val set = mutableSetOf<MetricEvent>()

    when {
      SystemInfoRt.isWindows -> {
        val processList = OSProcessUtil.getProcessList()
        for (process in processList) {
          when (process.executableName.lowercase()) {
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

        if (Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == true) {
          set.add(OS_HIGH_CONTRAST.metric(true))
        }
      }
      SystemInfoRt.isMac -> {
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
    }

    return set
  }
}