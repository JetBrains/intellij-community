// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.SystemInfoRt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class ProcessTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun assumeShell() {
      assumeTrue(SystemInfoRt.isUnix)
      assumeTrue {
        runCatching {
          runBlocking {
            runProcess(args = listOf("sh", "--version"))
          }
        }.isSuccess
      }
    }
  }

  private fun runShell(@Suppress("SameParameterValue") code: String, timeout: Duration) {
    val script = Files.createTempFile("script", ".sh").toFile()
    try {
      script.writeText(code)
      assertThat(script.setExecutable(true)).isTrue()
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(script.toPath(), PosixFilePermission.entries.toSet())
      }
      runBlocking {
        runProcess(args = listOf("sh", script.absolutePath), timeout = timeout)
      }
    }
    finally {
      Files.deleteIfExists(script.toPath())
    }
  }

  @Test
  fun success() {
    runShell(code = "sleep 1", timeout = DEFAULT_TIMEOUT)
  }

  @Test
  fun timeout() {
    try {
      runShell(code = "sleep 1", timeout = 10.milliseconds)
      throw AssertionError("Timeout had no effect")
    }
    catch (_: TimeoutCancellationException) {
    }
  }

  @Test
  fun threadDump() {
    runBlocking {
      dumpThreads(pid = ProcessHandle.current().pid())
    }
  }
}