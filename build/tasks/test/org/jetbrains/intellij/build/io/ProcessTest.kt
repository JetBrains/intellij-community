// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("UsePropertyAccessSyntax")

package org.jetbrains.intellij.build.io

import com.intellij.openapi.util.SystemInfoRt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class ProcessTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun assumeShell() {
      assumeTrue(SystemInfoRt.isUnix)
      assumeTrue {
        runCatching {
          runProcess("sh", "--version")
        }.isSuccess
      }
    }
  }

  @AfterEach
  fun allErrorOutputReadersAreDone() {
    assertThat(areAllIoTasksCompleted()).isTrue()
  }

  private fun runShell(@Suppress("SameParameterValue") code: String, timeoutMillis: Long) {
    val script = Files.createTempFile("script", ".sh").toFile()
    try {
      script.writeText(code)
      assertThat(script.setExecutable(true)).isTrue()
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(script.toPath(), PosixFilePermission.values().toSet())
      }
      runProcess("sh", script.absolutePath, timeoutMillis = timeoutMillis)
    }
    finally {
      Files.deleteIfExists(script.toPath())
    }
  }

  @Test
  fun success() {
    runShell(code = "sleep 1", timeoutMillis = Timeout.DEFAULT)
  }

  @Test
  fun timeout() {
    try {
      runShell(code = "sleep 1", timeoutMillis = 10L)
      throw AssertionError("Timeout had no effect")
    }
    catch (ignore: ProcessRunTimedOut) {
    }
  }

  @Test
  fun threadDump() {
    dumpThreads(ProcessHandle.current().pid())
  }
}