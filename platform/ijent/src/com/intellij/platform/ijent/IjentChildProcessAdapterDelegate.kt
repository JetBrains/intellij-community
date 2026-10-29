// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.provider.utils.asOutputStream
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.eel.terminate
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@VisibleToClasses(
  "com.intellij.platform.ijent.IjentChildProcessAdapter",
  "com.intellij.platform.ijent.IjentChildPtyProcessAdapter",
)
internal class IjentChildProcessAdapterDelegate(
  val coroutineScope: CoroutineScope,
  val ijentChildProcess: EelProcess,
) {
  val inputStream: InputStream = ijentChildProcess.stdout.consumeAsInputStream(coroutineScope.coroutineContext)

  val outputStream: OutputStream = ijentChildProcess.stdin.asOutputStream(coroutineScope.coroutineContext)

  val errorStream: InputStream = ijentChildProcess.stderr.consumeAsInputStream(coroutineScope.coroutineContext)

  /**
   * Previously, this code used to throw `IOException`. That was a bad idea:
   * the documentation of `java.lang.Process.waitFor` explicitly says, the method may throw only `InterruptedException`.
   * Users of this process adapter have never expected to catch anything from this method, so it led to errors.
   *
   * We could throw a RuntimeException. It would resolve the contract violation on paper, but the users still would not expect it.
   *
   * The only thing we can afford is to return some bogus error code.
   */
  @Throws(InterruptedException::class)
  fun waitFor(): Int = runBlockingInContext {
    try {
      ijentChildProcess.exitCode.await()
    }
    catch (err: SafeDeferred.DeferredException) {
      IjentLogger.EXEC_LOG.debug(err) {
        "Can't wait for the process $ijentChildProcess"
      }

      EXIT_CODE_WHEN_SCOPE_CANCELED
    }
  }

  @Throws(InterruptedException::class)
  fun waitFor(timeout: Long, unit: TimeUnit): Boolean = runBlockingInContext {
    withTimeoutOrNull(unit.toMillis(timeout).milliseconds) {
      try {
        ijentChildProcess.exitCode.await()
      }
      catch (err: SafeDeferred.DeferredException) {
        if (err.cause !is EelUnavailableException) {
          throw RuntimeException("Can't wait for the process", err)
        }
      }
      true
    } == true
  }

  fun destroyForcibly() {
    coroutineScope.launch {
      ijentChildProcess.kill()
    }
  }

  fun isAlive(): Boolean = when (ijentChildProcess.exitCode.state) {
    SafeDeferred.State.Active -> true
    is SafeDeferred.State.Finished<*> -> false
  }

  fun onExit(): CompletableFuture<Any?> =
    @Suppress("UNCHECKED_CAST") (ijentChildProcess.exitCode.asCompletableFuture() as CompletableFuture<Any?>)

  fun exitValue(): Int = when (val s = ijentChildProcess.exitCode.state) {
    is SafeDeferred.State.Completed -> s.value

    SafeDeferred.State.Active -> throw IllegalThreadStateException()

    is SafeDeferred.State.Unsuccessful -> EXIT_CODE_WHEN_SCOPE_CANCELED
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
  fun <T> runBlockingInContext(body: suspend () -> T): T = @Suppress("SSBasedInspection") runBlocking(coroutineScope.coroutineContext) {
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

  fun supportsNormalTermination(): Boolean = when (ijentChildProcess) {
    is EelPosixProcess -> true
    is EelWindowsProcess -> false
  }
}

/**
 * Possible options:
 *
 * * Return -1
 *     - Pros: Even though it is not documented, OpenJDK does that in rare cases itself.
 *       See https://github.com/JetBrains/JetBrainsRuntime/blob/1f7925ceb4460b1052c93969749a52487bf133fc/src/java.base/unix/native/libjava/ProcessHandleImpl_unix.c#L194
 *     - Pros: None of operating systems returns a negative error code.
 *       Therefore, it's clear from the first glance that something different happened.
 *     - Cons: Difficult to grep in case of problems.
 * * Return 137 (128 + SIGKILL)
 *     - Pros: Looks trustworthy.
 *     - Cons: it's much worse lie than everything else, because it gives false impressions about what actually happened.
 * * Return [Int.MIN_VALUE]
 *     - Pros: Undoubtedly clear that something went wrong.
 *     - Cons: `MIN_VALUE & 0xFF == 0`
 *       In case of conversion of int to byte, it can give a false positive result.
 *     - Cons: Difficult to grep
 * * Return [Int.MAX_VALUE]
 *     - Pros: Same as for [Int.MIN_VALUE]
 *     - Pros: Unlike [Int.MIN_VALUE], `MAX_VALUE & 0xFF == 0xFF`. It's not zero.
 *     - Cons: Difficult to grep
 * * Return a recognizable magic number. For example, 331, 0x331 (Eel in 1337sp34k), 0x331ed, 0x331f411, etc.
 *     - Pros: memorable, distinguishable, greppable.
 *     - Cons: Unconventional.
 *
 * Only -1 and magic constant numbers are good options.
 * Finally, -1 is chosen because there are already some comparisons to -1 in our code that uses the legacy SSH backend,
 * even though these usages are from another API.
 */
private const val EXIT_CODE_WHEN_SCOPE_CANCELED = -1