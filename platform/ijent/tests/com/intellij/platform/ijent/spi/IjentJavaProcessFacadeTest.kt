// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.milliseconds
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

  /**
   * The JDK reaper puts the bytes left in a pipe into a `ByteArrayInputStream` when the process exits.
   * A facade built after that reads these bytes and then EOF.
   */
  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `facade of a process with drained output reads the drained bytes`(): Unit = timeoutRunBlocking(2.minutes) {
    val parentJob = SupervisorJob(coroutineContext.job)
    try {
      val parent = ParentOfIjentScopes(CoroutineScope(coroutineContext + parentJob))
      val process = ProcessBuilder("/bin/sh", "-c", "echo out-$MARKER; echo err-$MARKER >&2; exit 5").start()
      awaitStreamsDrainedByJdk(process)
      assertTrue(innerStream(process.inputStream) is ByteArrayInputStream, "stdout: ${innerStream(process.inputStream)}")
      assertTrue(innerStream(process.errorStream) is ByteArrayInputStream, "stderr: ${innerStream(process.errorStream)}")

      val facade = IjentSessionProcessMediator.JavaProcessFacade(parent.createIjentScope("drained output test"), process)

      assertEquals("out-$MARKER\n", readAll(facade.stdout))
      assertEquals("err-$MARKER\n", readAll(facade.stderr))
      assertEquals(5, facade.exitCode.await())
    }
    finally {
      parentJob.cancel()
    }
  }

  /**
   * The JDK reaper puts `ProcessBuilder.NullInputStream` and `ProcessBuilder.NullOutputStream` in place of empty pipes.
   * A facade built after that reads EOF, and a send to stdin fails with an [IOException].
   */
  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `facade of a process with no output reads EOF and fails to send`(): Unit = timeoutRunBlocking(2.minutes) {
    val parentJob = SupervisorJob(coroutineContext.job)
    try {
      val parent = ParentOfIjentScopes(CoroutineScope(coroutineContext + parentJob))
      val process = ProcessBuilder("/bin/sh", "-c", "exit 0").start()
      awaitStreamsDrainedByJdk(process)
      assertEquals(NULL_INPUT_STREAM, innerStream(process.inputStream).javaClass.name)
      assertEquals(NULL_INPUT_STREAM, innerStream(process.errorStream).javaClass.name)

      val facade = IjentSessionProcessMediator.JavaProcessFacade(parent.createIjentScope("no output test"), process)

      assertEquals("", readAll(facade.stdout))
      assertEquals("", readAll(facade.stderr))
      val error = assertThrows<EelSendChannelException> { facade.stdin.sendWholeText("data\n") }
      assertTrue(error.cause is IOException, "cause: ${error.cause}")
      assertEquals(0, facade.exitCode.await())
    }
    finally {
      parentJob.cancel()
    }
  }

  /** The JDK reaper swaps stdout, stderr, then stdin, after `waitFor()` returns. */
  private suspend fun awaitStreamsDrainedByJdk(process: Process) {
    while (innerStream(process.outputStream).javaClass.name != NULL_OUTPUT_STREAM) {
      delay(10.milliseconds)
    }
  }

  private fun innerStream(stream: InputStream): InputStream =
    FilterInputStream::class.java.getDeclaredField("in").apply { isAccessible = true }.get(stream) as InputStream

  private fun innerStream(stream: OutputStream): OutputStream =
    FilterOutputStream::class.java.getDeclaredField("out").apply { isAccessible = true }.get(stream) as OutputStream

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
    const val NULL_INPUT_STREAM = "java.lang.ProcessBuilder\$NullInputStream"
    const val NULL_OUTPUT_STREAM = "java.lang.ProcessBuilder\$NullOutputStream"
  }
}
