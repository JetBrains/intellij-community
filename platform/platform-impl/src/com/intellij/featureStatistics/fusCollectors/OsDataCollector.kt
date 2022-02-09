// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByRegexp
import com.intellij.internal.statistic.eventLog.events.EventFields.Version
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.*
import kotlin.streams.asSequence

internal class OsDataCollector : ApplicationUsagesCollector(), AllowedDuringStartupCollector {
  private val OS_NAMES = listOf("Windows", "Mac", "Linux", "FreeBSD", "Solaris", "Other")

  private val LOCALES = listOf(
    "am", "ar", "as", "az", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fr", "gu", "ha", "hi", "hu", "ig", "in", "it", "ja", "kk",
    "kn", "ko", "ml", "mr", "my", "nb", "ne", "nl", "nn", "no", "or", "pa", "pl", "pt", "ro", "ru", "rw", "sd", "si", "so", "sv", "ta",
    "te", "th", "tr", "uk", "ur", "uz", "vi", "yo", "zh", "zu")

  @Suppress("SpellCheckingInspection")
  private val DISTROS = listOf(
    "almalinux", "alpine", "amzn", "arch", "bunsenlabs", "centos", "chromeos", "debian", "deepin", "devuan", "elementary", "fedora",
    "galliumos", "garuda", "gentoo", "kali", "linuxmint", "mageia", "manjaro", "neon", "nixos", "ol", "opensuse-leap", "opensuse-tumbleweed",
    "parrot", "pop", "pureos", "raspbian", "rhel", "rocky", "rosa", "sabayon", "slackware", "solus", "ubuntu", "void", "zorin",
    "other", "unknown")

  private val GROUP = EventLogGroup("system.os", 14)
  private val OS_NAME = String("name", OS_NAMES)
  private val OS_LANG = String("locale", LOCALES)
  private val OS_TZ = StringValidatedByRegexp("time_zone", "time_zone")
  private val OS = GROUP.registerVarargEvent("os.name", OS_NAME, Version, OS_LANG, OS_TZ)
  @ApiStatus.ScheduledForRemoval(inVersion = "2024.1")
  @Suppress("MissingDeprecatedAnnotationOnScheduledForRemovalApi")
  private val TIMEZONE = GROUP.registerEvent("os.timezone", StringValidatedByRegexp("value", "time_zone"))  // backward compatibility
  private val LINUX = GROUP.registerEvent("linux", String("distro", DISTROS), StringValidatedByRegexp("release", "version"), EventFields.Boolean("wsl"))
  private val WINDOWS = GROUP.registerEvent("windows", EventFields.Long("build"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val tz = getTimeZone()
    val metrics = mutableSetOf(
      OS.metric(OS_NAME.with(getOSName()), Version.with(SystemInfo.OS_VERSION), OS_LANG.with(getLanguage()), OS_TZ.with(tz)),
      TIMEZONE.metric(tz))

    when {
      SystemInfo.isLinux -> {
        val (distro, release) = getReleaseData()
        val isUnderWsl = detectIsUnderWsl()
        metrics += LINUX.metric(distro, release, isUnderWsl)
      }
      SystemInfo.isWin10OrNewer -> {
        // -1 is unknown
        metrics += WINDOWS.metric(SystemInfo.getWinBuildNumber() ?: -1)
      }
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

  private fun getLanguage(): String = Locale.getDefault().language

  private fun getTimeZone(): String = OffsetDateTime.now().offset.toString()

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

  private fun detectIsUnderWsl(): Boolean =
    try {
      @Suppress("SpellCheckingInspection") val kernel = Files.readString(Path.of("/proc/sys/kernel/osrelease"))
      kernel.contains("-microsoft-")
    }
    catch(e: IOException) {
      false
    }
}
