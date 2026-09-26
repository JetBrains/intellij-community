// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ClassName")

package com.intellij.platform.eel

import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendApi
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.eel.testFramework.bodyLimitedCoroutineScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.io.computeDetached
import com.intellij.util.io.toByteArray
import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("checkedExceptions")
internal class EelChannelInterruptionTest {
  private val onTearDown: MutableCollection<suspend () -> Unit> = CopyOnWriteArrayList()

  private fun onTearDown(body: suspend () -> Unit) {
    onTearDown.add(body)
  }

  @OptIn(DelicateCoroutinesApi::class)
  @AfterEach
  fun tearDown() {
    class NoopCoroutineExceptionHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler.Key), CoroutineExceptionHandler {
      override fun handleException(context: CoroutineContext, exception: Throwable): Unit = Unit
    }

    val tasks = onTearDown.toList()
    onTearDown.clear()
    val futures = tasks.reversed().map { task ->
      GlobalScope.async(Dispatchers.IO + NoopCoroutineExceptionHandler()) {
        task()
      }
    }
    timeoutRunBlocking(10.seconds) {
      var err: Throwable? = null
      for (future in futures) {
        try {
          future.await()
        }
        catch (_: CancellationException) {
          continue
        }
        catch (errorFromFuture: Throwable) {
          err = err?.apply { addSuppressed(errorFromFuture) } ?: errorFromFuture
        }
      }
      if (err != null) {
        throw err
      }
    }
  }

  /**
   * This 1 second is taken as a wild guess.
   * On the first hand, the maximal time of blocking a coroutine should be bearable in most cases.
   * On the other hand, the timeout must be big enough to mitigate test flakiness.
   */
  private val cancellationTimeout = 1.seconds

  @Test
  fun `EelReceiveChannel from java net Socket getInputStream`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val address = startSocketServer()

      val clientSocket = Socket()
      onTearDown {
        clientSocket.close()
      }
      clientSocket.connect(address)

      val channel = clientSocket.getInputStream().consumeAsEelChannel()

      val job = launchAndWaitUntilActualIoStarts {
        tryToReadFromEmptyChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelReceiveChannel from java nio channels SocketChannel`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val address = startSocketServer()

      val clientSocket = SocketChannel.open(address)
      onTearDown {
        clientSocket.close()
      }

      val channel = clientSocket.consumeAsEelChannel()

      val job = launchAndWaitUntilActualIoStarts {
        tryToReadFromEmptyChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelReceiveChannel from Process getInputStream`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val process = startProcess()

      val channel = process.inputStream.consumeAsEelChannel()

      val job = launchAndWaitUntilActualIoStarts {
        tryToReadFromEmptyChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelReceiveChannel from non-interruptible InputStream`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val channel = NonInterruptibleInputStream().consumeAsEelChannel()

      val job = launchAndWaitUntilNewMatchingCoroutineAppears("NonInterruptibleInputStream.read") {
        tryToReadFromEmptyChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelReceiveChannel from non-interruptible ReadableByteChannel`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val channel = NonInterruptibleByteChannel().consumeAsEelChannel()

      val job = launchAndWaitUntilNewMatchingCoroutineAppears("NonInterruptibleByteChannel.read") {
        tryToReadFromEmptyChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelSendChannel from java net Socket getOutputStream`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val address = startSocketServer()

      val clientSocket = Socket()
      onTearDown {
        clientSocket.close()
      }
      clientSocket.connect(address)

      val channel = clientSocket.getOutputStream().asEelChannel()

      val job = launchAndWaitUntilActualIoStarts {
        tryToWriteToBlockedChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelSendChannel from java nio channels SocketChannel`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val address = startSocketServer()

      val clientSocket = SocketChannel.open(address)
      onTearDown {
        clientSocket.close()
      }

      val channel = clientSocket.asEelChannel()

      val job = launchAndWaitUntilActualIoStarts {
        tryToWriteToBlockedChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelSendChannel from Process getOutputStream`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val process = startProcess()

      val channel = process.outputStream.asEelChannel()

      val job = launchAndWaitUntilActualIoStarts {
        tryToWriteToBlockedChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelSendChannel from non-interruptible OutputStream`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val channel = NonInterruptibleOutputStream().asEelChannel()

      val job = launchAndWaitUntilNewMatchingCoroutineAppears("NonInterruptibleOutputStream.write") {
        tryToWriteToBlockedChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun `EelSendChannel from non-interruptible WritableByteChannel`(): Unit = timeoutRunBlocking(20.seconds) {
    bodyLimitedCoroutineScope {
      val channel = NonInterruptibleWritableByteChannel().asEelChannel()

      val job = launchAndWaitUntilNewMatchingCoroutineAppears("NonInterruptibleWritableByteChannel.write") {
        tryToWriteToBlockedChannel(channel)
      }
      withTimeout(cancellationTimeout) {
        job.cancelAndJoin()
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun withDebugProbes2(body: suspend () -> Unit) {
    if (DebugProbes.isInstalled) {
      body()
    }
    else {
      DebugProbes.install()
      try {
        body()
      }
      finally {
        DebugProbes.uninstall()
      }
    }
  }

  @Nested
  inner class `self test` {
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `launchAndWaitUntilNewMatchingCoroutineAppears works as expected`(): Unit = timeoutRunBlocking(20.seconds, "test") {
      fun qweasdzxc() {
        Thread.sleep(2_000)
      }

      launchAndWaitUntilNewMatchingCoroutineAppears(::qweasdzxc.name) {
        computeDetached {
          delay(100.milliseconds)
          qweasdzxc()
        }
      }

      withContext(NonCancellable) { // To avoid confusion with the exception from `timeoutRunBlocking`.
        shouldThrow<TimeoutCancellationException> {
          launchAndWaitUntilNewMatchingCoroutineAppears("pooipouooiuoiuou", timeout = 1.seconds) {
            computeDetached {
              delay(100.milliseconds)
              qweasdzxc()
            }
          }
        }
      }
    }
  }

  private suspend fun CoroutineScope.launchAndWaitUntilActualIoStarts(body: suspend () -> Unit): Job {
    return launchAndWaitUntilNewMatchingCoroutineAppears(
      "FileInputStream.readBytes",
      "FileOutputStream.writeBytes",
      "sun.nio.ch.SelectorImpl.select",
      "SocketDispatcher.read0",
      "SocketDispatcher.write0",
      body = body,
    )
  }

  /**
   * This function helps to reach the state when some I/O operation really starts.
   * Without this method a race condition would exist, when `cancelAndJoin` in tests could
   * cancel the coroutine before the being tested I/O starts.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun CoroutineScope.launchAndWaitUntilNewMatchingCoroutineAppears(
    vararg patterns: String,
    timeout: Duration = 5.seconds,
    body: suspend () -> Unit,
  ): Job {
    lateinit var job: Job
    withDebugProbes2 {
      fun CoroutineInfo.coroutineId(): Int =
        Regex(""".*CoroutineId\((\d+)\).*""")
          .matchEntire(toString())
          ?.groupValues
          ?.get(1)
          ?.toInt()
        ?: -1

      val coroutineIdsBeforeTest = DebugProbes.dumpCoroutinesInfo().mapTo(HashSet()) { it.coroutineId() }

      fun getAllStackTraceLines(): MutableSet<String> {
        val threadNamePartsToTrack: Collection<String> =
          DebugProbes.dumpCoroutinesInfo().asSequence()
            .filter { coroutineInfo ->
              when (coroutineInfo.state) {
                State.CREATED -> false
                State.SUSPENDED -> true
                State.RUNNING -> true
              }
            }
            .map { it.coroutineId() }
            .filterNot(coroutineIdsBeforeTest::contains)
            .mapTo(mutableListOf()) { "@coroutine#$it" }

        return Thread.getAllStackTraces().asSequence()
          .filter { (thread, _) -> threadNamePartsToTrack.any(thread.name::contains) }
          .flatMap { (_, stackTrace) -> stackTrace.asSequence() }
          .mapTo(hashSetOf()) { stackTraceElement -> stackTraceElement.toString() }
      }

      val seenStackTraceElements = getAllStackTraceLines()
      job = this@launchAndWaitUntilNewMatchingCoroutineAppears.launch { body() }
      withTimeout(timeout) {  // withTimeout throws TimeoutCancellationException, and timeoutRunBlocking writes a coroutine dump on it.
        mainLoop@ while (true) {
          val stackTracesNow = getAllStackTraceLines()
          for (element in stackTracesNow) {
            if (seenStackTraceElements.add(element) && patterns.any(element::contains)) {
              break@mainLoop
            }
          }
          delay(50.milliseconds)
        }
      }
    }
    return job
  }

  private suspend fun tryToReadFromEmptyChannel(channel: EelReceiveChannel): Nothing {
    val buffer = ByteBuffer.allocate(1024)
    val result = channel.receive(buffer)  // Should exit from cancellation exception here.
    fail {
      buffer.flip()
      "Received result: $result. Content: ${buffer.flip().toByteArray().contentToString()}"
    }
  }

  @OptIn(EelSendApi::class)
  private suspend fun tryToWriteToBlockedChannel(channel: EelSendChannel): Nothing {
    val buffer = ByteBuffer.allocate(1024 * 1024)
    while (true) {
      channel.send(buffer)
      if (!buffer.hasRemaining()) {
        buffer.clear()
      }
    }
  }

  private fun startProcess(): Process {
    val process = ProcessBuilder()
      .command(
        when (localEel.descriptor.osFamily) {
          EelOsFamily.Posix -> listOf("sleep", "300")
          EelOsFamily.Windows -> listOf(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-NonInteractive",
            "-Command",
            "Start-Sleep -Seconds 60",
          )
        })
      .start()
    onTearDown {
      process.destroy()
    }
    return process
  }

  private fun CoroutineScope.startSocketServer(): SocketAddress {
    val serverSocket = ServerSocket()
    onTearDown {
      serverSocket.close()
    }

    serverSocket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    launch {
      val socket = serverSocket.accept()
      onTearDown {
        socket.close()
      }
      // Never reading from the socket, never writing into it. Just keeping it open.
    }

    return serverSocket.localSocketAddress
  }

  private inner class NonInterruptibleOutputStream : java.io.OutputStream() {
    @Volatile
    private var keepWaiting = true

    init {
      onTearDown {
        keepWaiting = false
      }
    }

    override fun write(b: Int) {
      infiniteWait()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      if (len > 0) infiniteWait()
    }

    private fun infiniteWait(): Nothing {
      while (keepWaiting) {
        try {
          Thread.sleep(10)
        }
        catch (_: InterruptedException) {
          // Deliberately ignored.
        }
      }
      error("unreachable")
    }
  }

  private inner class NonInterruptibleWritableByteChannel : java.nio.channels.WritableByteChannel {
    @Volatile
    private var keepWaiting = true

    init {
      onTearDown {
        keepWaiting = false
      }
    }

    override fun write(src: ByteBuffer): Int {
      infiniteWait()
    }

    override fun isOpen(): Boolean = true

    override fun close(): Unit = Unit

    private fun infiniteWait(): Nothing {
      while (keepWaiting) {
        try {
          Thread.sleep(10)
        }
        catch (_: InterruptedException) {
          // Deliberately ignored.
        }
      }
      error("unreachable")
    }
  }

  private inner class NonInterruptibleInputStream : InputStream() {
    @Volatile
    private var keepWaiting = true

    init {
      onTearDown {
        keepWaiting = false
      }
    }

    override fun read(): Int {
      while (keepWaiting) {
        try {
          Thread.sleep(10)
        }
        catch (_: InterruptedException) {
          // Deliberately ignored.
        }
      }
      error("unreachable")
    }
  }

  private inner class NonInterruptibleByteChannel : ByteChannel {
    @Volatile
    private var keepWaiting = true

    init {
      onTearDown {
        keepWaiting = false
      }
    }

    override fun read(dst: ByteBuffer): Int {
      infiniteWait()
    }

    override fun isOpen(): Boolean = true

    override fun close(): Unit = Unit

    override fun write(src: ByteBuffer): Int {
      infiniteWait()
    }

    private fun infiniteWait(): Nothing {
      while (keepWaiting) {
        try {
          Thread.sleep(10)
        }
        catch (_: InterruptedException) {
          // Deliberately ignored.
        }
      }
      error("unreachable")
    }
  }
}
