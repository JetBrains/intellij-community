// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByRegexpReference
import com.intellij.internal.statistic.eventLog.events.EventFields.Version
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.UnixUtil.getGlibcVersion
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.*
import kotlin.io.path.name
import kotlin.streams.asSequence

internal class OsDataCollector : ApplicationUsagesCollector() {
  private val OS_NAMES = listOf("Windows", "Mac", "Linux", "FreeBSD", "Solaris", "Other")

  private val LOCALES = listOf(
    "am", "ar", "as", "az", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fr", "gu", "ha", "hi", "hu", "ig", "in", "it", "ja", "kk",
    "kn", "ko", "ml", "mr", "my", "nb", "ne", "nl", "nn", "no", "or", "pa", "pl", "pt", "ro", "ru", "rw", "sd", "si", "so", "sv", "ta",
    "te", "th", "tr", "uk", "ur", "uz", "vi", "yo", "zh", "zu")

  @Suppress("SpellCheckingInspection")
  private val SHELLS = listOf("sh", "ash", "bash", "csh", "dash", "fish", "ksh", "tcsh", "xonsh", "zsh", "nu", "other", "unknown")

  @Suppress("SpellCheckingInspection")
  private val DISTROS = listOf(
    "almalinux", "alpine", "amzn", "arch", "bunsenlabs", "centos", "chromeos", "debian", "deepin", "devuan", "elementary",
    "endeavouros", "fedora", "galliumos", "garuda", "gentoo", "kali", "linuxmint", "mageia", "manjaro", "neon", "nixos", "ol",
    "opensuse-leap", "opensuse-tumbleweed", "parrot", "pop", "pureos", "raspbian", "rhel", "rocky", "rosa", "sabayon",
    "slackware", "solus", "ubuntu", "void", "zorin", "other", "unknown")

  private val GROUP = EventLogGroup("system.os", 18)
  private val OS_NAME = String("name", OS_NAMES)
  private val OS_LANG = String("locale", LOCALES)
  private val OS_TZ = StringValidatedByRegexpReference("time_zone", "time_zone")
  private val OS_SHELL = String("shell", SHELLS)
  private val DISTRO = String("distro", DISTROS)
  private val RELEASE = StringValidatedByRegexpReference("release", "version")
  private val UNDER_WSL = EventFields.Boolean("wsl")
  private val GLIBC = StringValidatedByRegexpReference("glibc", "version")

  private val OS = GROUP.registerVarargEvent("os.name", OS_NAME, Version, OS_LANG, OS_TZ, OS_SHELL)
  @ApiStatus.ScheduledForRemoval(inVersion = "2024.1")
  @Suppress("MissingDeprecatedAnnotationOnScheduledForRemovalApi", "ScheduledForRemovalWithVersion")
  private val TIMEZONE = GROUP.registerEvent("os.timezone", StringValidatedByRegexpReference("value", "time_zone"))  // backward compatibility
  private val LINUX = GROUP.registerVarargEvent("linux", DISTRO, RELEASE, UNDER_WSL, GLIBC)
  private val WINDOWS = GROUP.registerEvent("windows", EventFields.Long("build"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val tz = getTimeZone()
    val metrics = mutableSetOf(
      OS.metric(OS_NAME.with(getOSName()), Version.with(SystemInfo.OS_VERSION), OS_LANG.with(getLanguage()), OS_TZ.with(tz), OS_SHELL.with(getShell())),
      TIMEZONE.metric(tz))
    when {
      SystemInfo.isLinux -> {
        val (distro, release) = getReleaseData()
        val isUnderWsl = detectIsUnderWsl()
        val glibcVersion = getGlibcVersion()
        val linuxMetrics = mutableListOf<EventPair<*>>(DISTRO.with(distro), RELEASE.with(release), UNDER_WSL.with(isUnderWsl))
        if (glibcVersion != null) {
          linuxMetrics.add(GLIBC.with(glibcVersion.toString()))
        }
        metrics += LINUX.metric(*linuxMetrics.toTypedArray())
      }
      SystemInfo.isWin10OrNewer -> {
        metrics += WINDOWS.metric(SystemInfo.getWinBuildNumber() ?: -1)  // `-1` is unknown
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

  private fun getShell(): String? =
    if (SystemInfo.isWindows) null
    else SHELLS.coerce(runCatching { System.getenv("SHELL")?.let { Path.of(it).name } }.getOrNull())

  // https://www.freedesktop.org/software/systemd/man/os-release.html
  private fun getReleaseData(): Pair<String, String?> =
    try {
      Files.lines(Path.of("/etc/os-release")).use { lines ->
        val fields = setOf("ID", "VERSION_ID")
        val values = lines.asSequence()
          .map { it.split('=') }
          .filter { it.size == 2 && it[0] in fields }
          .associate { it[0] to it[1].trim('"') }
        val distro = DISTROS.coerce(values["ID"])
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
    catch (e: IOException) {
      false
    }

  private fun List<String>.coerce(value: String?): String =
    when (value) {
      null -> "unknown"
      in this -> value
      else -> "other"
    }
}
