// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

@Timeout(30)
class IjentShellBootstrapTest {
  @Test
  fun `bootstrap marker classifies POSIX and Windows shells`() {
    val marker = "IJENT_SHELL_PROBE_test"

    assertEquals(DetectedShell.Posix, parseShellMarker("$marker OS=Linux ARCH=x86_64 SHELL=/bin/bash", marker))
    assertTrue(parseShellMarker("$marker OS=Windows_NT ARCH=AMD64 SHELL=cmd.exe", marker) is DetectedShell.PowerShell)
    assertTrue(parseShellMarker("$marker OS=Windows_NT ARCH=AMD64 SHELL=powershell.exe", marker) is DetectedShell.PowerShell)
    assertTrue(parseShellMarker("$marker OS=MINGW64_NT-10.0 ARCH=x86_64 SHELL=/bin/bash", marker) is DetectedShell.PowerShell)
  }

  @Test
  fun `Windows POSIX environments select native PowerShell`() {
    val marker = "IJENT_SHELL_PROBE_test"
    for (os in listOf("CYGWIN_NT-10.0-22631", "MINGW64_NT-10.0", "MSYS_NT-10.0")) {
      assertEquals(DetectedShell.PowerShell, parseShellMarker("$marker OS=$os ARCH=x86_64 SHELL=/bin/bash", marker), os)
    }
  }

  @Test
  fun `native POSIX environments retain the POSIX dialect`() {
    val marker = "IJENT_SHELL_PROBE_test"
    for (os in listOf("Linux", "Darwin")) {
      assertEquals(DetectedShell.Posix, parseShellMarker("$marker OS=$os ARCH=arm64 SHELL=/bin/zsh", marker), os)
    }
  }

  @Test
  fun `bootstrap marker is found after a banner or prompt`() {
    val marker = "IJENT_SHELL_PROBE_test"
    assertEquals(
      DetectedShell.Posix,
      parseShellMarker("welcome> $marker OS=Darwin ARCH=arm64 SHELL=/bin/zsh", marker),
    )
    assertNull(parseShellMarker("unrelated login banner", marker))
  }

  @Test
  fun `malformed tagged marker is rejected`() {
    val marker = "IJENT_SHELL_PROBE_test"
    assertThrows(EelUnavailableException.CommunicationFailure::class.java) {
      parseShellMarker("$marker malformed", marker)
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `POSIX bootstrap keeps the same channel alive for subsequent commands`() {
    val bootstrap = createShellBootstrap()
    val process = ProcessBuilder("/bin/sh", "-c", bootstrap.command).start()
    process.outputStream.bufferedWriter().use {
      it.appendLine("echo IJENT_AFTER_BOOTSTRAP")
      it.appendLine("exit 0")
    }
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()

    assertEquals(0, process.waitFor(), error)
    assertTrue(output.lineSequence().any { it.contains(bootstrap.marker) }, output)
    assertTrue(output.lineSequence().any { it == "IJENT_AFTER_BOOTSTRAP" }, output)
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `Windows command bootstrap starts PowerShell from its first line`() {
    val bootstrap = createShellBootstrap()
    val process = ProcessBuilder("cmd.exe", "/c", bootstrap.command.substringBefore('\n')).start()
    process.outputStream.bufferedWriter().use {
      it.appendLine("[Console]::Out.WriteLine('IJENT_AFTER_BOOTSTRAP')")
      it.appendLine("exit 0")
    }
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()

    assertEquals(0, process.waitFor(), error)
    assertTrue(output.lineSequence().any { it.contains("${bootstrap.marker} OS=Windows_NT") }, output)
    assertTrue(output.lineSequence().any { it == "IJENT_AFTER_BOOTSTRAP" }, output)
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `the recovery bootstrap starts PowerShell without PATH`() {
    val command = getIjentShellBootstrapCommand().substringBefore('\n')
    val builder = ProcessBuilder("${System.getenv("SystemRoot")}\\System32\\cmd.exe", "/c", command)
    builder.environment()["PATH"] = ""
    val process = builder.start()
    try {
      process.outputStream.bufferedWriter().use {
        it.appendLine("[Console]::Out.WriteLine('IJENT_RECOVERY_READY')")
        it.appendLine("exit 0")
      }
      val output = process.inputStream.bufferedReader().readText()
      val error = process.errorStream.bufferedReader().readText()
      assertEquals(0, process.waitFor(), error)
      assertTrue(output.lineSequence().any { it == "IJENT_RECOVERY_READY" }, output)
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  fun `native PowerShell bootstrap keeps the same channel alive`() {
    val bootstrap = createShellBootstrap()
    val encoded = Base64.getEncoder().encodeToString(bootstrap.command.toByteArray(Charsets.UTF_16LE))
    val process = ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).start()
    try {
      process.outputStream.bufferedWriter().use {
        it.appendLine("[Console]::Out.WriteLine('IJENT_AFTER_BOOTSTRAP')")
        it.appendLine("exit 0")
      }
      val output = process.inputStream.bufferedReader().readText()
      val error = process.errorStream.bufferedReader().readText()
      assertEquals(0, process.waitFor(), error)
      assertTrue(output.contains("${bootstrap.marker} OS=Windows_NT"), output)
      assertTrue(output.lineSequence().any { it == "IJENT_AFTER_BOOTSTRAP" }, output)
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `Cygwin bootstrap launches PowerShell through the POSIX shell`(@TempDir directory: Path) {
    val uname = directory.resolve("uname")
    Files.writeString(uname, "#!/bin/sh\necho CYGWIN_NT-10.0\n")
    val powershell = directory.resolve("powershell.exe")
    Files.writeString(powershell, "#!/bin/sh\nprintf '%s\\n' \"$*\"\n/bin/cat\n")
    for (executable in listOf(uname, powershell)) {
      Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"))
    }
    val bootstrap = createShellBootstrap()
    val builder = ProcessBuilder("/bin/sh", "-c", bootstrap.command)
    builder.environment()["PATH"] = directory.toString()
    val process = builder.start()
    process.outputStream.bufferedWriter().use { it.appendLine("IJENT_AFTER_BOOTSTRAP") }
    val output = process.inputStream.bufferedReader().readText()
    val error = process.errorStream.bufferedReader().readText()

    assertEquals(0, process.waitFor(), error)
    assertTrue(output.contains("${bootstrap.marker} OS=CYGWIN_NT-10.0"), output)
    assertTrue(output.lineSequence().any { it == "-NoLogo -NoProfile -NonInteractive -Command -" }, output)
    assertTrue(output.lineSequence().any { it == "IJENT_AFTER_BOOTSTRAP" }, output)
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `bootstrap uses BusyBox when uname is unavailable`(@TempDir root: Path) {
    for (os in listOf("Linux", "Darwin", "CYGWIN_NT-10.0", "MINGW64_NT-10.0", "MSYS_NT-10.0")) {
      checkBusyBoxBootstrap(os, Files.createDirectory(root.resolve(os)))
    }
  }

  private fun checkBusyBoxBootstrap(os: String, directory: Path) {
    val busybox = directory.resolve("busybox")
    Files.writeString(busybox, $$"#!/bin/sh\n[ \"$1\" = uname ] || exit 1\ncase \"$2\" in -s) echo $$os ;; -m) echo x86_64 ;; esac\n")
    val powershell = directory.resolve("powershell.exe")
    Files.writeString(powershell, "#!/bin/sh\n/bin/cat\n")
    for (executable in listOf(busybox, powershell)) {
      Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"))
    }
    val bootstrap = createShellBootstrap()
    val builder = ProcessBuilder("/bin/sh", "-c", bootstrap.command)
    builder.environment()["PATH"] = directory.toString()
    val process = builder.start()
    try {
      process.outputStream.bufferedWriter().use {
        it.appendLine("echo IJENT_AFTER_BOOTSTRAP")
        it.appendLine("exit 0")
      }
      val output = process.inputStream.bufferedReader().readText()
      val error = process.errorStream.bufferedReader().readText()
      assertEquals(0, process.waitFor(), error)
      val marker = output.lineSequence().first { it.contains(bootstrap.marker) }
      assertTrue(marker.contains("OS=$os ARCH=x86_64"), output)
      val expectedShell = if (os == "Linux" || os == "Darwin") DetectedShell.Posix else DetectedShell.PowerShell
      assertEquals(expectedShell, parseShellMarker(marker, bootstrap.marker))
      assertTrue(output.contains("IJENT_AFTER_BOOTSTRAP"), output)
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `bootstrap rejects an unknown OS when both uname commands are unavailable`(@TempDir directory: Path) {
    val bootstrap = createShellBootstrap()
    val builder = ProcessBuilder("/bin/sh", "-c", bootstrap.command)
    builder.environment()["PATH"] = directory.toString()
    val process = builder.start()
    try {
      process.outputStream.close()
      val output = process.inputStream.bufferedReader().readText()
      val error = process.errorStream.bufferedReader().readText()
      assertEquals(0, process.waitFor(), error)
      val marker = output.lineSequence().first { it.contains(bootstrap.marker) }
      assertThrows(EelUnavailableException.CommunicationFailure::class.java) { parseShellMarker(marker, bootstrap.marker) }
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `shared strategy detects a local shell without SSH or IJent`(): Unit = timeoutRunBlocking {
    val strategy = object : IjentDeployingOverShellProcessStrategy.WithShellBootstrap(ParentOfIjentScopes(this), Dispatchers.IO, "local bootstrap test") {
      override val ijentExecFileProvider: IjentExecFileProvider = object : IjentExecFileProvider {
        override suspend fun getIjentBinary(targetPlatform: EelPlatform): Path = error("The shell probe must not request an IJent binary")
      }

      override suspend fun mapPath(path: Path): String? = null

      override suspend fun createShellProcessFacade(
        ijentProcessScope: IjentScope,
        script: String,
      ): IjentSessionProcessMediator.ProcessFacade =
        IjentSessionProcessMediator.JavaProcessFacade(ijentProcessScope, ProcessBuilder("/bin/sh", "-c", script).start())

      suspend fun detectPlatform(): EelPlatform = getTargetPlatform()

      fun closeStrategy() = close()
    }

    try {
      assertTrue(strategy.detectPlatform() is EelPlatform.Posix)
    }
    finally {
      strategy.closeStrategy()
    }
  }
}
