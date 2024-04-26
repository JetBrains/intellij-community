// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ijent.*
import com.intellij.platform.util.coroutines.channel.ChannelInputStream
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.future.asCompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal class IjentChildProcessAdapterDelegate(
  val ijentId: IjentId,
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

  @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
  fun tryDestroyGracefully(): Boolean {
    // TODO The whole implementation of this method is a dirty hotfix. The IDE should not rely on /bin/kill.
    //  Instead, IJent must be able to send SIGINT by itself: IJPL-148611
    val ijentApi = IjentSessionRegistry.instance().ijents[ijentId] ?: return false
    GlobalScope.launch(blockingDispatcher) {
      val error: Any? = when (val p = ijentApi.exec.executeProcess("kill", "-SIGINT", "--", "-" + ijentChildProcess.pid.toString())) {
        is IjentExecApi.ExecuteProcessResult.Success -> {
          p.process.getOutput().takeIf { it.exitCode != 0 }
        }
        is IjentExecApi.ExecuteProcessResult.Failure -> p
      }
      if (error != null) {
        LOG.error("Failed to kill WSL process with PID ${ijentChildProcess.pid}: $error")
      }
    }
    return true
  }

  /** TODO Copy-pasted from tests. This function should be moved to a shared place. */
  private suspend fun IjentChildProcess.getOutput(): ProcessOutput {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val exitCode: Int
    coroutineScope {
      launch {
        this@getOutput.stdout.consumeEach(stdout::write)
      }
      launch {
        this@getOutput.stderr.consumeEach(stderr::write)
      }
      exitCode = this@getOutput.exitCode.await()
    }

    return ProcessOutput(
      stdout.toString(StandardCharsets.UTF_8),
      stderr.toString(StandardCharsets.UTF_8),
      exitCode,
      false,
      false,
    )
  }

  companion object {
    private val LOG = logger<IjentChildProcessAdapterDelegate>()
  }
}