// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.provider.utils.asOutputStream
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.ijent.spi.IjentThreadPool
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
  val ijentChildProcess: EelProcess,
) {
  val inputStream: InputStream = ijentChildProcess.stdout.consumeAsInputStream(coroutineScope.coroutineContext)

  val outputStream: OutputStream = ijentChildProcess.stdin.asOutputStream(coroutineScope.coroutineContext)

  val errorStream: InputStream = ijentChildProcess.stderr.consumeAsInputStream(coroutineScope.coroutineContext)

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
      when (ijentChildProcess) {
        is EelPosixProcess -> ijentChildProcess.terminate()
        is EelWindowsProcess -> ijentChildProcess.kill()
      }
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

  fun supportsNormalTermination(): Boolean =
    when (ijentChildProcess) {
      is EelPosixProcess -> true
      is EelWindowsProcess -> false
    }
}