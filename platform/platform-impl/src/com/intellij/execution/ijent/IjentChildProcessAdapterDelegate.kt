// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.platform.ijent.IjentChildProcess
import com.intellij.platform.util.coroutines.channel.ChannelInputStream
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
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
  val inputStream: InputStream = ChannelInputStream(coroutineScope, ijentChildProcess.stdout)

  val outputStream: OutputStream = IjentStdinOutputStream(coroutineScope.coroutineContext, ijentChildProcess)

  val errorStream: InputStream = ChannelInputStream(coroutineScope, ijentChildProcess.stderr)

  @RequiresBackgroundThread
  @Throws(InterruptedException::class)
  fun waitFor(): Int =
    runBlockingInContext {
      ijentChildProcess.exitCode.await()
    }

  @RequiresBackgroundThread
  @Throws(InterruptedException::class)
  fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
    runBlockingInContext {
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
  fun <T> runBlockingInContext(body: suspend () -> T): T =
    @Suppress("SSBasedInspection") runBlocking(coroutineScope.coroutineContext) {
      body()
    }
}