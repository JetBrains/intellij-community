// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.String
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByRegexpReference
import com.intellij.internal.statistic.eventLog.events.EventFields.Version
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.io.path.name

@OptIn(LowLevelLocalMachineAccess::class)
internal class OsDataCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("system.os", 22)

  private val OS_NAMES = listOf("Windows", "Mac", "Linux", "FreeBSD", "Other")

  private val LOCALES = listOf(
    "am", "ar", "as", "az", "bn", "cs", "da", "de", "el", "en", "es", "fa", "fr", "gu", "ha", "hi", "hu", "ig", "in", "it", "ja", "kk",
    "kn", "ko", "ml", "mr", "my", "nb", "ne", "nl", "nn", "no", "or", "pa", "pl", "pt", "ro", "ru", "rw", "sd", "si", "so", "sv", "ta",
    "te", "th", "tr", "uk", "ur", "uz", "vi", "yo", "zh", "zu"
  )

  @Suppress("SpellCheckingInspection")
  private val SHELLS = listOf("sh", "ash", "bash", "csh", "dash", "fish", "ksh", "pwsh", "tcsh", "xonsh", "zsh", "nu", "other", "unknown")

  @Suppress("SpellCheckingInspection")
  private val DISTROS = listOf(
    "almalinux", "alpine", "amzn", "arch", "bunsenlabs", "cachyos", "centos", "chromeos", "debian", "deepin", "devuan",
    "elementary", "endeavouros", "fedora", "galliumos", "garuda", "gentoo", "kali", "linuxmint", "mageia", "manjaro",
    "neon", "nixos", "nobara", "ol", "omarchy", "opensuse-leap", "opensuse-tumbleweed", "parrot", "pop", "pureos",
    "raspbian", "rhel", "rocky", "rosa", "sabayon", "slackware", "solus", "ubuntu", "void", "zorin", "other", "unknown"
  )

  private val OS_NAME = String("name", OS_NAMES)
  private val OS_LANG = String("locale", LOCALES)
  private val OS_TZ = StringValidatedByRegexpReference("time_zone", "time_zone")
  private val OS_SHELL = String("shell", SHELLS)
  private val DISTRO = String("distro", DISTROS)
  private val RELEASE = StringValidatedByRegexpReference("release", "version")
  private val UNDER_WSL = EventFields.Boolean("wsl")
  private val HAS_GDBUS = EventFields.Boolean("gdbus")
  private val HAS_XDG_OPEN = EventFields.Boolean("xdg_open")
  private val GLIBC = StringValidatedByRegexpReference("glibc", "version")

  private val OS_EVENT = GROUP.registerVarargEvent("os.name", OS_NAME, Version, OS_LANG, OS_TZ, OS_SHELL)
  private val LINUX = GROUP.registerVarargEvent("linux", DISTRO, RELEASE, UNDER_WSL, HAS_GDBUS, HAS_XDG_OPEN, GLIBC)
  private val WINDOWS = GROUP.registerEvent("windows", EventFields.Long("build"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val tz = getTimeZone()
    val metrics = mutableSetOf(
      OS_EVENT.metric(OS_NAME.with(getOSName()), Version.with(OS.CURRENT.version()), OS_LANG.with(getLanguage()), OS_TZ.with(tz), OS_SHELL.with(getShell()))
    )
    if (OS.CURRENT == OS.Linux) {
      val osInfo = OS.CURRENT.osInfo as OS.LinuxInfo
      val linuxMetrics = mutableListOf(
        DISTRO.with(DISTROS.coerce(osInfo.distro)),
        RELEASE.with(osInfo.release),
        UNDER_WSL.with(osInfo.isUnderWsl()),
        HAS_GDBUS.with(PathEnvironmentVariableUtil.isOnPath("gdbus")),
        HAS_XDG_OPEN.with(PathEnvironmentVariableUtil.isOnPath("xdg-open")),
      )
      osInfo.glibcVersion?.let {
        linuxMetrics.add(GLIBC.with(it))
      }
      metrics += LINUX.metric(*linuxMetrics.toTypedArray())
    }
    else if (OS.CURRENT == OS.Windows) {
      metrics += WINDOWS.metric((OS.CURRENT.osInfo as OS.WindowsInfo).buildNumber ?: -1)  // `-1` is unknown
    }
    return metrics
  }

  private fun getOSName(): String = when (OS.CURRENT) {
    OS.Windows -> "Windows"
    OS.macOS -> "Mac"
    OS.Linux -> "Linux"
    OS.FreeBSD -> "FreeBSD"
    OS.Other -> "Other"
  }

  private fun getLanguage(): String = Locale.getDefault().language

  private fun getTimeZone(): String = OffsetDateTime.now().offset.toString()

  private fun getShell(): String? =
    if (OS.CURRENT == OS.Windows) null
    else SHELLS.coerce(runCatching { System.getenv("SHELL")?.let { Path.of(it).name } }.getOrNull())

  private fun List<String>.coerce(value: String?): String = when (value) {
    null -> "unknown"
    in this -> value
    else -> "other"
  }
}
