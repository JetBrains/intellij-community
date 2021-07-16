// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByInlineRegexp
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByRegexp
import com.intellij.internal.statistic.eventLog.events.EventFields.Version
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.SystemInfo
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.*
import kotlin.math.roundToInt
import kotlin.streams.asSequence

internal class OsDataCollector : ApplicationUsagesCollector() {
  private val OS_NAMES = listOf("Windows", "Mac", "Linux", "FreeBSD", "Solaris", "Other")

  @Suppress("SpellCheckingInspection")
  private val DISTROS = listOf(
    "almalinux", "alpine", "amzn", "arch", "bunsenlabs", "centos", "chromeos", "debian", "deepin", "devuan", "elementary", "fedora",
    "galliumos", "garuda", "gentoo", "kali", "linuxmint", "mageia", "manjaro", "neon", "nixos", "ol", "opensuse-leap", "opensuse-tumbleweed",
    "parrot", "pop", "pureos", "raspbian", "rhel", "rocky", "rosa", "sabayon", "slackware", "solus", "ubuntu", "void", "zorin",
    "other", "unknown")

  private val GROUP = EventLogGroup("system.os", 9)
  private val NAME = GROUP.registerEvent("os.name", String("name", OS_NAMES), Version, StringValidatedByInlineRegexp("locale", "[a-z]{2}"))
  private val TIMEZONE = GROUP.registerEvent("os.timezone", Int("value"))
  private val LINUX = GROUP.registerEvent("linux", String("distro", DISTROS), StringValidatedByRegexp("release", "version"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val metrics = mutableSetOf(
      NAME.metric(getOSName(), SystemInfo.OS_VERSION, Locale.getDefault().language),
      TIMEZONE.metric((OffsetDateTime.now().offset.totalSeconds / 3600.0).roundToInt()))
    if (SystemInfo.isLinux) {
      val (distro, release) = getReleaseData()
      metrics += LINUX.metric(distro, release)
    }
    return metrics
  }

  private fun getOSName(): String =
    when {
      SystemInfo.isWindows -> "Windows"
      SystemInfo.isMac -> "Mac"
      SystemInfo.isLinux -> "Linux"
      SystemInfo.isFreeBSD -> "FreeBSD"
      SystemInfo.isSolaris -> "Solaris"
      else -> "Other"
    }

  // https://www.freedesktop.org/software/systemd/man/os-release.html
  private fun getReleaseData(): Pair<String, String?> =
    try {
      Files.lines(Path.of("/etc/os-release")).use { lines ->
        val fields = setOf("ID", "VERSION_ID")
        val values = lines.asSequence()
          .map { it.split('=') }
          .filter { it.size == 2 && it[0] in fields }
          .associate { it[0] to it[1].trim('"') }
        val distro = when (val id = values["ID"]) {
          null -> "unknown"
          in DISTROS -> id
          else -> "other"
        }
        distro to values["VERSION_ID"]
      }
    }
    catch (ignored: IOException) {
      "unknown" to null
    }
}
