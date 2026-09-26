// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Suppress("checkedExceptions")
class NioReadToEelAdapterCancellationTest {
  /**
   * A stream-backed channel ([InputStream.consumeAsEelChannel]) must be read detached, so that cancelling the read does
   * not close the channel on the cancelling thread.
   *
   * This reproduces the shutdown freeze where the cancelling thread (the EDT) blocked in a native `close()` behind a
   * pending blocking read. The blocking `close()` is simulated here so the test is meaningful on every platform (on a
   * real Windows pipe the same close blocks; on Unix it does not).
   */
  @Test
  fun `cancelling a blocked stream-backed read does not block the canceller`(): Unit = runBlocking {
    val readEntered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val stream = object : InputStream() {
      override fun read(): Int {
        readEntered.countDown()
        release.await()
        return -1
      }

      override fun read(b: ByteArray, off: Int, len: Int): Int {
        readEntered.countDown()
        release.await()
        return -1
      }

      // Simulates a Windows pipe: closing while a read is pending blocks until the read is released.
      override fun close() {
        release.await()
      }
    }

    val job = launch(Dispatchers.IO) {
      stream.consumeAsEelChannel().receive(ByteBuffer.allocate(16))
    }
    check(readEntered.await(10, TimeUnit.SECONDS)) { "the read did not start" }

    val cancelReturned = CountDownLatch(1)
    thread(name = "canceller") {
      job.cancel()
      cancelReturned.countDown()
    }

    // With streamBacked reads detached, cancellation must not block the canceller even though close() blocks.
    cancelReturned.await(10, TimeUnit.SECONDS) shouldBe true

    // Let the detached read and its (blocking) close finish so the launched job can complete.
    release.countDown()
  }
}
