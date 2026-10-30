// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.minutes

class IjentJavaProcessFacadeTest {
  /**
   * The JDK reaper drains the unread bytes of a pipe into its own buffer and closes the pipe when the process exits.
   * A fast exit races the reader of the raw pipe, so the race needs many runs.
   * The shell waits on stdin until the facade exists: an exit before it breaks the raw stream lookup.
   */
  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `stderr of a process that exits at once reaches the channel`(): Unit = timeoutRunBlocking(2.minutes) {
    val parentJob = SupervisorJob(coroutineContext.job)
    try {
      val parent = ParentOfIjentScopes(CoroutineScope(coroutineContext + parentJob))
      val lost = (1..RUNS).mapNotNull { run ->
        val ijentScope = parent.createIjentScope("stderr test $run")
        val process = ProcessBuilder("/bin/sh", "-c", "read _; echo $MARKER >&2; exit 3").start()
        val facade = IjentSessionProcessMediator.JavaProcessFacade(ijentScope, process)
        process.outputStream.close()
        // Odd runs read at once and race the exit, even runs read after the exit, like a reader that starts late.
        if (run % 2 == 0) facade.exitCode.await()
        val stderr = readAll(facade.stderr)
        assertEquals(3, facade.exitCode.await())
        if (stderr.trim() == MARKER) null else "run $run: <$stderr>"
      }
      assertEquals(emptyList<String>(), lost, "${lost.size} of $RUNS runs lost stderr")
    }
    finally {
      parentJob.cancel()
    }
  }

  private suspend fun readAll(channel: EelReceiveChannel): String {
    val result = ByteArrayOutputStream()
    val buffer = ByteBuffer.allocate(8192)
    try {
      while (channel.receive(buffer) != ReadResult.EOF) {
        buffer.flip()
        result.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
        buffer.clear()
      }
    }
    catch (e: EelReceiveChannelException) {
      return "${result.toString(Charsets.UTF_8)} [$e]"
    }
    finally {
      channel.closeForReceive()
    }
    return result.toString(Charsets.UTF_8)
  }

  private companion object {
    const val MARKER = "marker"
    const val RUNS = 50
  }
}
