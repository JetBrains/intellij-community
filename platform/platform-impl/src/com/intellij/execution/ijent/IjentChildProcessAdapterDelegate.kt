// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.platform.ijent.IjentChildProcess
import com.intellij.util.channel.ChannelInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal class IjentChildProcessAdapterDelegate(
  val coroutineScope: CoroutineScope,
  val ijentChildProcess: IjentChildProcess,
) {
  val inputStream: InputStream = ChannelInputStream(ijentChildProcess.stdout)

  val outputStream: OutputStream = IjentStdinOutputStream(coroutineScope.coroutineContext, ijentChildProcess)

  val errorStream: InputStream = ChannelInputStream(ijentChildProcess.stderr)

  @Throws(InterruptedException::class)
  fun waitFor(): Int =
    runBlockingInterruptible {
      ijentChildProcess.exitCode.await()
    }

  fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
    runBlockingInterruptible {
      withTimeoutOrNull(unit.toMillis(timeout).milliseconds) {
        ijentChildProcess.exitCode.await()
        true
      }
      ?: false
    }

  fun destroyForcibly() {
    coroutineScope.launch {
      ijentChildProcess.kill()
    }
  }

  fun isAlive(): Boolean =
    !ijentChildProcess.exitCode.isCompleted

  fun onExit(): CompletableFuture<Any?> =
    ijentChildProcess.exitCode.asCompletableFuture()

  fun exitValue(): Int =
    if (ijentChildProcess.exitCode.isCompleted)
      @Suppress("SSBasedInspection") (runBlocking { ijentChildProcess.exitCode.await() })
    else
      throw IllegalThreadStateException()

  fun destroy() {
    coroutineScope.launch {
      ijentChildProcess.terminate()
    }
  }

  @Throws(InterruptedException::class)
  fun <T> runBlockingInterruptible(body: suspend () -> T): T =
    @Suppress("SSBasedInspection") runBlocking(coroutineScope.coroutineContext) {
      try {
        body()
      }
      catch (err: CancellationException) {
        Thread.currentThread().interrupt()
        throw InterruptedException(err.message)
      }
    }
}