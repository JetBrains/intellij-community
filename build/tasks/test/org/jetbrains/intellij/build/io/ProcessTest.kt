// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import org.junit.*
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class ProcessTest {
  companion object {
    @BeforeClass
    @JvmStatic
    fun assumeShell() {
      try {
        runProcess("sh", "--version")
      }
      catch (e: Throwable) {
        Assume.assumeNoException(e)
      }
    }
  }

  @After
  fun allErrorOutputReadersAreDone() {
    val errorOutputReaders = Thread.getAllStackTraces().keys.filter {
      it.name.startsWith(errorOutputReaderNamePrefix)
    }
    assert(errorOutputReaders.isEmpty()) {
      errorOutputReaders.joinToString { it.name }
    }
  }

  private fun runShell(@Suppress("SameParameterValue") code: String, timeoutMillis: Long) {
    val script = Files.createTempFile("script", ".sh").toFile()
    try {
      script.writeText(code)
      Assert.assertTrue(script.setExecutable(true))
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
    catch (e: ProcessRunTimedOut) {
    }
  }

  @Test
  fun threadDump() {
    dumpThreads(ProcessHandle.current().pid())
  }
}