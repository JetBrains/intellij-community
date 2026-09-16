// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.IjentUnavailableException
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
import java.nio.file.Path

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
    assertThrows(IjentUnavailableException.CommunicationFailure::class.java) {
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
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `shared strategy detects a local shell without SSH or IJent`(): Unit = timeoutRunBlocking {
    val strategy = object : IjentDeployingOverShellProcessStrategy.WithShellBootstrap(ParentOfIjentScopes(this), Dispatchers.IO) {
      override val ijentLabel: String = "local bootstrap test"

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
