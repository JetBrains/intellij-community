// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ErrorString
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.EelPipe
import com.intellij.platform.eel.provider.utils.asOutputStream
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.eel.provider.utils.copy
import com.intellij.platform.ijent.IjentChildProcess
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal class IjentChildProcessAdapterDelegate(
  val coroutineScope: CoroutineScope,
  val ijentChildProcess: IjentChildProcess,
  redirectStderr: Boolean,
) {
  val inputStream: InputStream

  val outputStream: OutputStream = ijentChildProcess.stdin.asOutputStream(coroutineScope.coroutineContext)

  val errorStream: InputStream

  init {
    if (redirectStderr) {
      val pipe = EelPipe()

      coroutineScope.launch {
        launch {
          copyToPipe(ijentChildProcess.stdout, pipe)
        }
        launch {
          copyToPipe(ijentChildProcess.stderr, pipe)
        }
        launch {
          ijentChildProcess.exitCode.await()
          // When the process dies, we close its stream.
          // That will be done by ijentChildProcess anyway, but the operation is idempotent
          pipe.closePipe()
        }
      }

      inputStream = pipe.source.consumeAsInputStream(coroutineScope.coroutineContext)
      errorStream = ByteArrayInputStream(byteArrayOf())
    }
    else {
      inputStream = ijentChildProcess.stdout.consumeAsInputStream(coroutineScope.coroutineContext)
      errorStream = ijentChildProcess.stderr.consumeAsInputStream(coroutineScope.coroutineContext)
    }
  }

  /**
   * Copy from [from] to [pipe] and close [pipe] once read finished.
   * When `stderr` redirected to `stdout` we close `stdout` as soon as either `stderr` or `stdout` gets closed
   */
  private suspend fun copyToPipe(from: EelReceiveChannel<ErrorString>, pipe: EelPipe) {
    when (val r = copy(from, pipe.sink)) {
      is EelResult.Error -> pipe.closePipe(IOException("pipe closed: ${r.error}"))
      is EelResult.Ok -> pipe.closePipe()
    }
  }

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
      } == true
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
      IjentThreadPool.checkCurrentThreadIsInPool()
      body()
    }

  @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
  fun tryDestroyGracefully(): Boolean {
    coroutineScope.launch {
      ijentChildProcess.interrupt()
    }
    return true
  }
}