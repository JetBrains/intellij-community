// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.lang.IllegalStateException

class WSLInstallationsCollector : ApplicationUsagesCollector() {
  companion object {
    private val group = EventLogGroup("wsl.installations", 1)
    private val installationCountEvent = group.registerEvent("count", EventFields.Int("version"), EventFields.Int("count"))
    private val LOG = logger<WSLInstallationsCollector>()
  }

  override fun getGroup(): EventLogGroup {
    return Companion.group
  }

  override fun getMetrics(): Set<MetricEvent> {
    if (!WSLUtil.isSystemCompatible()) return emptySet()

    val distributionsWithVersions = try {
      WslDistributionManager.getInstance().loadInstalledDistributionsWithVersions()
    }
    catch(e: IOException) {
      LOG.warn("Failed to load installed WSL distributions: " + e.message)
      return emptySet()
    }
    catch (e: IllegalStateException) {
      LOG.error(e)
      return emptySet()
    }

    val installations = distributionsWithVersions.groupBy { it.version }
    return installations.mapNotNullTo(HashSet()) { (version, distributions) ->
      installationCountEvent.metric(version, distributions.size)
    }
  }
}
