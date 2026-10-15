// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.convertToJVMProcess
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.readAllBytes
import com.intellij.platform.eel.spawnProcess
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * `destroy` of a local process can close its stdout and stderr. A read after that must return EOF, not throw.
 */
@Suppress("checkedExceptions")
@TestApplication
internal class StreamClosedAwareEelReceiveChannelTest {
  @Test
  fun `stdout and stderr can be read till EOF after kill`(): Unit = timeoutRunBlocking(1.minutes) {
    val process = startShell()
    process.kill()

    process.assertOutputIsReadTillEof()
  }

  @Test
  fun `stdout and stderr can be read till EOF after destroy of the JVM process from the outside`(): Unit = timeoutRunBlocking(1.minutes) {
    val process = startShell()
    process.convertToJVMProcess().destroyForcibly()
    // The fallback checks that the process is not alive.
    process.exitCode.await()

    process.assertOutputIsReadTillEof()
  }

  private suspend fun startShell(): EelProcess {
    val (shell, arg) = localEel.exec.getShell()
    val command = when (localEel.descriptor.osFamily) {
      EelOsFamily.Posix -> "sleep 300; true"
      EelOsFamily.Windows -> "pause"
    }
    return localEel.exec.spawnProcess(shell, arg, command).eelIt()
  }

  /**
   * The shell can write some data before `destroy` (for example, `pause` writes a prompt), and on Windows `destroy` keeps the streams open.
   * Thus, read till EOF: the read must not throw.
   */
  private suspend fun EelProcess.assertOutputIsReadTillEof() {
    stdout.readAllBytes()
    stderr.readAllBytes()
  }
}
