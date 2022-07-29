// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.Logger

class StartupPerformanceCollector : CounterUsagesCollector() {
  companion object {
    private val LOG: Logger = Logger.getInstance(StartupPerformanceCollector::class.java)

    private val GROUP = EventLogGroup("startup", 4)

    private val DURATION = EventFields.Int("duration")

    private val SPLASH_SHOWN = GROUP.registerEvent("splashShown", DURATION)
    private val SPLASH_HIDDEN = GROUP.registerEvent("splashHidden", DURATION)
    private val PROJECT_FRAME_VISIBLE = GROUP.registerEvent("projectFrameVisible", DURATION)
    private val TOTAL_DURATION = GROUP.registerEvent("totalDuration", DURATION)

    private val BOOTSTRAP = GROUP.registerEvent("bootstrap", DURATION)
    private val SPLASH = GROUP.registerEvent("splash", DURATION)
    private val APP_INIT = GROUP.registerEvent("appInit", DURATION)

    fun logEvent(eventId: String, duration: Int) {
      when (eventId) {
        "bootstrap" -> BOOTSTRAP.log(duration)
        "splash" -> SPLASH.log(duration)
        "app initialization" -> APP_INIT.log(duration)
        "event:splash shown" -> SPLASH_SHOWN.log(duration)
        "event:splash hidden" -> SPLASH_HIDDEN.log(duration)
        "projectFrameVisible" -> PROJECT_FRAME_VISIBLE.log(duration)
        "totalDuration" -> TOTAL_DURATION.log(duration)
        else -> LOG.error("Trying to log unknown startup performance metric ('$eventId'). To record it, register a corresponding event and increment group version.")
      }
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}