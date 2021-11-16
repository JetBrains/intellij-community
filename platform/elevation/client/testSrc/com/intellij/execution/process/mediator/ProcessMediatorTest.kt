// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.process.mediator.client.MediatedProcess
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.launcher.ProcessMediatorConnection
import com.intellij.execution.process.mediator.launcher.startInProcessServer
import com.intellij.execution.process.mediator.rt.MediatedProcessTestMain
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.stream.Collectors
import java.util.stream.Stream

open class ProcessMediatorTest {
  private val deferred = CompletableDeferred<Unit>()
  private val coroutineScope = CoroutineScope(deferred + CoroutineExceptionHandler { _, cause ->
    if (cause !is CancellationException) {
      deferred.completeExceptionally(cause)  // to propagate child failures past supervisor jobs up to the top
    }
  })

  private var connection: ProcessMediatorConnection? = null
  private val client: ProcessMediatorClient
    get() = connection!!.client

  private val startedProcesses = mutableListOf<Process>()

  protected open fun createProcessMediatorConnection(coroutineScope: CoroutineScope, testInfo: TestInfo): ProcessMediatorConnection {
    val bindName = testInfo.testMethod.orElse(null)?.name ?: testInfo.displayName
    return ProcessMediatorConnection.startInProcessServer(coroutineScope, bindName)
  }

  private val TIMEOUT_MS = 3000.toLong()

  @BeforeEach
  fun start(testInfo: TestInfo) {
    connection = createProcessMediatorConnection(coroutineScope, testInfo)
  }

  @AfterEach
  fun tearDown() {
    val watchdogJob = coroutineScope.launch {
      delay(TIMEOUT_MS)
      deferred.completeExceptionally(TimeoutException("tearDown() timed out"))
    }
    startedProcesses.forEach {
      destroyProcess(it)
    }
    connection?.close()
    watchdogJob.cancel()
    runBlocking {
      deferred.complete(Unit)
      deferred.await()  // throw in case any background coroutine failed with uncaught exception
    }
    assertFalse(deferred.isCancelled)
  }

  @Test
  internal fun `error on create process`() {
    val path = "/path/to/non-existent/binary"
    assertFalse(FileUtil.exists(path))
    assertThrows(IOException::class.java) {
      ProcessBuilder(path).startMediatedProcess()
    }
  }

  @Test
  internal fun `create simple process returning zero exit code`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.True::class)
      .startMediatedProcess()
    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    assertEquals(0, process.exitValue())
  }

  @Test
  internal fun `create simple process returning non-zero exit code`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.False::class)
      .startMediatedProcess()
    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    assertEquals(42, process.exitValue())
  }

  @Test
  internal fun `destroy process with endless loop`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.Loop::class)
      .startMediatedProcess()
    val pid = process.pid()
    assertTrue(process.isAlive)
    assertTrue(ProcessHandle.of(pid).isPresent)

    destroyProcess(process)
  }

  @Test
  internal fun `destroy exited process`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.True::class)
      .startMediatedProcess()

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    assertTrue(ProcessHandle.of(process.pid()).isEmpty)
    assertFalse(process.isAlive)
  }

  @Test
  internal fun `write to closed stream`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.True::class)
      .startMediatedProcess()

    process.outputStream.close()

    assertThrows(IOException::class.java) {
      OutputStreamWriter(process.outputStream).use {
        it.write("test")
        it.flush()
      }
    }
  }

  @Test
  internal fun `write to externally closed stream`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.StreamInterruptor::class)
      .startMediatedProcess()

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertFalse(hasExited)

    assertThrows(IOException::class.java) {
      OutputStreamWriter(process.outputStream).use {
        it.write("test\n")
        it.flush()
      }
    }
  }

  @Test
  internal fun `read from closed stream`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.True::class)
      .startMediatedProcess()

    try {
      process.inputStream.close()
      assertThrows(IOException::class.java) {
        process.inputStream.read()
      }

      process.errorStream.close()
      assertThrows(IOException::class.java) {
        process.errorStream.read()
      }
    }
    finally {
      destroyProcess(process)
      assertNotEquals(-1, process.exitValue())
    }
  }

  @Test
  internal fun `read from externally closed stream`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.StreamInterruptor::class)
      .startMediatedProcess()

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertFalse(hasExited)

    try {
      assertEquals(-1, process.inputStream.read())
      assertEquals(-1, process.errorStream.read())
    }
    finally {
      destroyProcess(process)
      assertNotEquals(-1, process.exitValue())
    }
  }

  @Test
  internal fun `close streams and read (write) externally`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.TestClosedStream::class)
      .startMediatedProcess()

    try {
      process.inputStream.close()
      process.errorStream.close()
      process.outputStream.close()

      val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
      assertTrue(hasExited)
      // If something goes wrong in TestClosedStream app, it returns non-zero exit code
      assertEquals(0, process.exitValue())
    }
    finally {
      destroyProcess(process)
      assertNotEquals(-1, process.exitValue())
    }
  }

  @Test
  internal fun `test echo process`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.Echo::class)
      .startMediatedProcess()

    OutputStreamWriter(process.outputStream).use {
      it.write("Hello ")
      it.flush()
      Thread.sleep(500)
      it.write("World\n")
      it.flush()
    }

    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    assertEquals("Hello World\n", output)

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    assertFalse(process.isAlive)
  }

  @Test
  internal fun `input redirect`(@TempDir tmpDir: Path) {
    val content = "test data"
    val inputFile = File(tmpDir.toFile(), "input.txt")
    inputFile.writeText(content)

    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.Echo::class)
      .redirectInput(inputFile)
      .startMediatedProcess()

    // Writing to the redirected stream is not allowed.
    assertThrows(IOException::class.java) {
      OutputStreamWriter(process.outputStream).use {
        it.write("this wouldn't work\n")
        it.flush()
      }
    }

    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    assertEquals(content + '\n', output)

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    destroyProcess(process)
  }

  @Test
  internal fun `output redirect`(@TempDir tmpDir: Path) {
    val outputFile = File(tmpDir.toFile(), "output.txt")
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.Echo::class)
      .redirectOutput(outputFile)
      .startMediatedProcess()

    val content = "test data\n"
    OutputStreamWriter(process.outputStream).use {
      it.write(content)
      it.flush()
    }

    // Reading from a redirected stream will always result in empty output.
    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    assertEquals("", output)

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    destroyProcess(process)

    assertEquals(content, outputFile.readText())
  }

  @Test
  internal fun `error redirect`(@TempDir tmpDir: Path) {
    val errorFile = File(tmpDir.toFile(), "error.txt")
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.Echo::class)
      .apply { command().add("with_stderr") }
      .redirectError(errorFile)
      .startMediatedProcess()

    val content = "test data\n"
    OutputStreamWriter(process.outputStream).use {
      it.write(content)
      it.flush()
    }

    // Reading from a redirected stream will always result in empty output.
    val output = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
    assertEquals("", output)

    val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertTrue(hasExited)
    destroyProcess(process)

    assertEquals(content, errorFile.readText())
  }

  @Test
  internal fun `stress test input - output`() {
    val builder = createProcessBuilderForJavaClass(MediatedProcessTestMain.Echo::class).apply {
      command().add("with_stderr")
    }
    val process = builder.startMediatedProcess()

    val executor = Executors.newFixedThreadPool(3)
    try {
      val expectedData = executor.submit(Callable {
        var expected = ""
        OutputStreamWriter(process.outputStream).use {
          for (i in 0..1000) {
            val content = Stream.generate { "ping! ping! ping!\n" }
              .limit(Random().nextInt(1000).toLong())
              .collect(Collectors.joining())
            it.write(content)
            it.flush()

            expected += content
          }
        }
        expected
      })

      val inputData = executor.submit(Callable {
        BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
      })

      val errorData = executor.submit(Callable {
        BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
      })

      val expected = expectedData.get(60, TimeUnit.SECONDS)
      assertEquals(expected, inputData.get(60, TimeUnit.SECONDS))
      assertEquals(expected, errorData.get(60, TimeUnit.SECONDS))

      val hasExited = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
      assertTrue(hasExited)
      assertFalse(process.isAlive)
      destroyProcess(process)
    }
    finally {
      executor.shutdown()
    }
  }

  private fun destroyProcess(process: Process) {
    try {
      process.destroy()
      assertTrue(process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS))
      assertFalse(process.isAlive)
      assertTrue(ProcessHandle.of(process.pid()).isEmpty)
    }
    finally {
      process.destroyForcibly()
    }
  }

  private fun ProcessBuilder.startMediatedProcess(): Process {
    return MediatedProcess.create(client, this).also {
      startedProcesses += it
    }
  }
}
