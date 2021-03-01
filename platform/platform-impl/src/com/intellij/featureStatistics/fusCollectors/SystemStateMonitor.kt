// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.concurrency.JobScheduler
import com.intellij.internal.DebugAttachDetector
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class SystemStateMonitor : FeatureUsageStateEventTracker {
  private val OS_GROUP = EventLogGroup("system.os", 6)
  private val INITIAL_DELAY = 5
  private val PERIOD_DELAY = 24 * 60

  private val SESSION_GROUP = EventLogGroup("event.log.session", 1)
  private val DEBUG = SESSION_GROUP.registerEvent("debug.mode", EventFields.Boolean("debug_agent"))
  private val REPORT = SESSION_GROUP.registerEvent(
    "reporting", EventFields.Boolean("suppress_report"), EventFields.Boolean("only_local")
  )
  private val TEST = SESSION_GROUP.registerEvent(
    "test.mode",
    EventFields.Boolean("fus_test"), EventFields.Boolean("internal"), EventFields.Boolean("teamcity")
  )
  private val HEADLESS = SESSION_GROUP.registerEvent(
    "headless", EventFields.Boolean("headless"), EventFields.Boolean("command_line")
  )

  override fun initialize() {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { reportNow() },
      INITIAL_DELAY.toLong(), PERIOD_DELAY.toLong(), TimeUnit.MINUTES
    )
  }

  override fun reportNow(): CompletableFuture<Void> {
    reportSessionInfo()

    val osEvents: MutableList<MetricEvent> = ArrayList()

    /** Record OS name in both old and new format to have a smooth transition on the server **/
    val dataOS = newDataWithOsVersion()
    osEvents.add(newMetric(getOSName(), dataOS))
    osEvents.add(newMetric("os.name", dataOS.copy().addData("name", getOSName())))

    /** writing current os timezone as os.timezone event_id **/
    val currentZoneOffset = OffsetDateTime.now().offset
    val currentZoneOffsetFeatureUsageData = FeatureUsageData().addData("value", currentZoneOffset.toString())
    osEvents.add(newMetric("os.timezone", currentZoneOffsetFeatureUsageData))
    val configuration = EventLogConfiguration.getOrCreate("FUS").machineIdConfiguration
    val machineId = MachineIdManager.getAnonymizedMachineId("JetBrainsFUS", configuration.salt)
    val data = FeatureUsageData().addData("id", machineId ?: "unknown")
    if (machineId != null) {
      data.addData("revision", configuration.revision)
    }
    osEvents.add(newMetric("machine.id", data))
    return FUStateUsagesLogger.logStateEventsAsync(OS_GROUP, osEvents)
  }

  private fun reportSessionInfo() {
    val events: MutableList<MetricEvent> = ArrayList()
    val app = ApplicationManager.getApplication()
    events.add(DEBUG.metric(DebugAttachDetector.isDebugEnabled()))
    events.add(REPORT.metric(isSuppressStatisticsReport(), isLocalStatisticsWithoutReport()))
    events.add(TEST.metric(StatisticsRecorderUtil.isTestModeEnabled("FUS"), app.isInternal, isTeamcityDetected()))
    events.add(HEADLESS.metric(app.isHeadlessEnvironment, app.isCommandLine))
    FUStateUsagesLogger.logStateEventsAsync(SESSION_GROUP, events)
  }

  private fun newDataWithOsVersion(): FeatureUsageData {
    val osData = FeatureUsageData()
    if (SystemInfo.isLinux) {
      val linuxRelease = OsVersionUsageCollector.getLinuxRelease()
      osData.addData("release", linuxRelease.release)
      osData.addVersionByString(linuxRelease.version)
    }
    else {
      osData.addVersion(OsVersionUsageCollector.parse(SystemInfo.OS_VERSION))
    }
    return osData
  }

  private fun getOSName() : String {
    return when {
      SystemInfo.isChromeOS -> "ChromeOS"
      SystemInfo.isLinux -> "Linux"
      SystemInfo.isMac -> "Mac"
      SystemInfo.isWindows -> "Windows"
      SystemInfo.isFreeBSD -> "FreeBSD"
      SystemInfo.isSolaris -> "Solaris"
      else -> "Other"
    }
  }
}
