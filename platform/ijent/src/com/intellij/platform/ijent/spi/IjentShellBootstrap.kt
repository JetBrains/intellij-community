// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.ijent.IjentUnavailableException.CommunicationFailure
import com.intellij.platform.ijent.spi.IjentSessionMediatorUtils.readLineOrThrow
import org.jetbrains.annotations.VisibleForTesting
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class ShellBootstrap(val marker: String, val command: String)

internal fun createShellBootstrap(): ShellBootstrap {
  val marker = "IJENT_SHELL_PROBE_${UUID.randomUUID().toString().replace("-", "")}"
  val command = $$"""
    echo \" <<'REM' >/dev/null ">NUL "\" \`" <#"
    @ECHO OFF
    ECHO $$marker OS=%OS% ARCH=%PROCESSOR_ARCHITECTURE% SHELL=cmd.exe
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -Command -
    EXIT /B %ERRORLEVEL%
    REM
    #> | Out-Null
    echo \" <<'POWERSHELL_SCRIPT' >/dev/null # " | Out-Null
    function global:prompt { ' ' }
    [Console]::Out.WriteLine("$$marker OS=$($env:OS) ARCH=$($env:PROCESSOR_ARCHITECTURE) SHELL=powershell.exe")
    & "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -Command -
    exit $LASTEXITCODE
    <#
    POWERSHELL_SCRIPT
    ijent_os=$(uname -s)
    echo "$$marker OS=$ijent_os ARCH=$(uname -m) SHELL=$0"
    case "$ijent_os" in
      CYGWIN*|MINGW*|MSYS*) exec powershell.exe -NoLogo -NoProfile -NonInteractive -Command - ;;
      *) exec /bin/sh ;;
    esac
    #>
  """.trimIndent()
  return ShellBootstrap(marker, command)
}

internal suspend fun detectBootstrappedShell(process: IjentSessionProcessMediator.ProcessFacade, marker: String): DetectedShell {
  try {
    while (true) {
      val line = process.stdout.readLineOrThrow(StandardCharsets.UTF_8).trim()
      parseShellMarker(line, marker)?.let { return it }
    }
  }
  catch (e: EelReceiveChannelException) {
    throw CommunicationFailure("Failed to read the target shell marker: ${e.message}", e)
  }
}

/** Parses probe output such as `IJENT_SHELL_PROBE_123 OS=Windows_NT ARCH=AMD64 SHELL=powershell.exe`. */
@VisibleForTesting
internal fun parseShellMarker(line: String, marker: String): DetectedShell? {
  val markerOffset = line.indexOf(marker)
  if (markerOffset < 0) return null
  val markerLine = line.substring(markerOffset)
  val os = markerLine.substringAfter(" OS=", missingDelimiterValue = "").substringBefore(' ')
  val shell = markerLine.substringAfterLast(" SHELL=", missingDelimiterValue = "")
  return when {
    os.equals("Windows_NT", ignoreCase = true) ||
    os.startsWith("CYGWIN", ignoreCase = true) ||
    os.startsWith("MINGW", ignoreCase = true) ||
    os.startsWith("MSYS", ignoreCase = true) ||
    shell.equals("cmd.exe", ignoreCase = true) || shell.equals("powershell.exe", ignoreCase = true) ->
      DetectedShell.PowerShell
    os.isNotEmpty() && shell.isNotEmpty() -> DetectedShell.Posix
    else -> throw CommunicationFailure("Malformed target shell marker: $markerLine", null)
  }
}

internal sealed interface DetectedShell {
  data object Posix : DetectedShell
  data object PowerShell : DetectedShell
}
