// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.nio.charset.StandardCharsets

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
    if (!SystemInfo.isWin10OrNewer) return emptySet()

    val wslExe = WSLDistribution.findWslExe() ?: return emptySet()
    val output = try {
      ExecUtil.execAndGetOutput(GeneralCommandLine(wslExe.toString(), "-l", "-v").withCharset(StandardCharsets.UTF_16LE), 10_000)
    }
    catch(e: ExecutionException) {
      LOG.info("Failed to run wsl: " + e.message)
      return emptySet()
    }

    if (output.exitCode != 0) {
      LOG.info("Failed to run wsl: exit code ${output.exitCode}, stderr ${output.stderr}")
      return emptySet()
    }

    //C:\JetBrains>wsl -l -v
    //  NAME      STATE           VERSION
    //* Ubuntu    Running         2
    val installations = output.stdoutLines
      .drop(1)
      .groupBy { it.substringAfterLast(' ') }
    return installations.mapNotNullTo(HashSet()) { (versionString, distributions) ->
      versionString.toIntOrNull()?.let { version ->
        installationCountEvent.metric(version, distributions.size)
      }
    }
  }
}
