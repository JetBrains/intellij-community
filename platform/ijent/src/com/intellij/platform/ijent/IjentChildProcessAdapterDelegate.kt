// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.provider.utils.asOutputStream
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.ijent.spi.IjentThreadPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
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

  @Throws(InterruptedException::class)
  fun waitFor(): Int =
    runBlockingInContext {
      try {
        ijentChildProcess.exitCode.await()
      }
      catch (err: SafeDeferred.DeferredException) {
        throw IOException("Can't wait for the process", err)
      }
    }

  @Throws(InterruptedException::class)
  fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
    runBlockingInContext {
      withTimeoutOrNull(unit.toMillis(timeout).milliseconds) {
        try {
          ijentChildProcess.exitCode.await()
        }
        catch (err: SafeDeferred.DeferredException) {
          throw IOException("Can't wait for the process", err)
        }
        true
      } == true
    }

  fun destroyForcibly() {
    coroutineScope.launch {
      ijentChildProcess.kill()
    }
  }

  fun isAlive(): Boolean =
    when (ijentChildProcess.exitCode.state) {
      SafeDeferred.State.Active -> true
      is SafeDeferred.State.Canceled, is SafeDeferred.State.Completed<*>, is SafeDeferred.State.Failed -> false
    }

  fun onExit(): CompletableFuture<Any?> =
    @Suppress("UNCHECKED_CAST")
    (ijentChildProcess.exitCode.asCompletableFuture() as CompletableFuture<Any?>)

  fun exitValue(): Int =
    when (val s = ijentChildProcess.exitCode.state) {
      is SafeDeferred.State.Completed ->
        s.value

      SafeDeferred.State.Active, is SafeDeferred.State.Canceled, is SafeDeferred.State.Failed ->
        throw IllegalThreadStateException()
    }

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

  @OptIn(DelicateCoroutinesApi::class)
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