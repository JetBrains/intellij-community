// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.PeekableEelReceiveChannel
import com.intellij.platform.eel.channels.peekable
import com.intellij.platform.eel.map
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.ijent.IjentLog
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.coroutineNameAppended
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.CHECK_CODE
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.NORMAL
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A wrapper for a [Process] that runs IJent. The wrapper logs stderr lines, waits for the exit code, terminates the process in case
 * of problems in the IDE.
 *
 * [processExit] never throws. When it completes, it either means that the process has finished, or that the whole scope of IJent processes
 * is canceled.
 *
 * [ijentProcessScope] should be used by the [com.intellij.platform.ijent.IjentApi] implementation for launching internal coroutines.
 * No matter if IJent exits expectedly or not, an attempt to do anything with [ijentProcessScope] after the IJent has exited
 * throws [IjentUnavailableException].
 */
class IjentSessionProcessMediator private constructor(
  override val ijentProcessScope: IjentScope,
  val process: ProcessFacade,
) : IjentSessionMediator {
  interface ProcessFacade {
    val stdin: EelSendChannel
    val stdout: PeekableEelReceiveChannel
    val stderr: EelReceiveChannel
    val exitCode: SafeDeferred<Int>
    suspend fun destroyForcibly()
    suspend fun destroy()

    val isAlive: Boolean
      get() = when (exitCode.state) {
        SafeDeferred.State.Active -> true
        is SafeDeferred.State.Finished -> false
      }
  }

  @OptIn(DelicateCoroutinesApi::class)
  class JavaProcessFacade(private val ijentProcessScope: IjentScope, private val process: Process) : ProcessFacade {
    // Pump the process std-streams on `IjentThreadPool` instead of the default `Dispatchers.IO`-backed
    // `unlimitedDispatcher`. The blocking native `read()`/`write()` calls park a worker thread for the whole
    // session; `DefaultDispatcher-worker-*` is not whitelisted by `ThreadLeakTracker`, while `IjentThreadPool-` is.
    // And it's also legit even not taking tests into account. The reason why IjentThreadPool exists is written in its docs,
    // and here's exactly a case described there.
    override val stdin: EelSendChannel = process.outputStream.asEelChannel(IjentThreadPool.coroutineContext)
    override val stdout: PeekableEelReceiveChannel = process.inputStream.consumeAsEelChannel(IjentThreadPool.coroutineContext).peekable()
    override val stderr: EelReceiveChannel = process.errorStream.consumeAsEelChannel(IjentThreadPool.coroutineContext)

    // Pin the blocking `Process.waitFor()` call to `IjentThreadPool` via the explicit
    // `runInterruptible` context. `Process.awaitExit()` would otherwise route the JDK
    // wait through `runInterruptible(Dispatchers.IO)`, parking a `DefaultDispatcher-worker-*`
    // thread that `ThreadLeakTracker.wellKnownOffenders` does not whitelist — so the
    // watcher would be reported as a leak whenever the ijent session is still alive at
    // the moment leak detection runs (e.g. an IDE Starter test on WSL where the manager
    // scope outlives the test). `IjentThreadPool-` is whitelisted, and `runInterruptible`
    // still delivers a thread interrupt on cancellation.
    override val exitCode: SafeDeferred<Int> = SafeDeferred(ijentProcessScope.parent.s.async {
      runInterruptible(IjentThreadPool.coroutineContext) {
        @Suppress("UsePlatformProcessAwaitExit")
        process.waitFor()
      }
      process.exitValue()
    })
    override val isAlive: Boolean get() = process.isAlive

    override suspend fun destroyForcibly() {
      withContext(blockingDispatcher) {
        process.destroyForcibly()
      }
    }

    override suspend fun destroy() {
      withContext(blockingDispatcher) {
        process.destroy()
      }
    }
  }

  override val processExit: SafeDeferred<Unit> = process.exitCode.map { Unit }

  /**
   * Defines how process exits should be handled in terms of error reporting.
   * Used to determine whether a process termination should be treated as an error.
   */
  enum class ProcessExitPolicy {
    /**
     * Check exit code to determine if it's an error.
     * Normal termination with expected exit codes is allowed.
     */
    CHECK_CODE,

    /**
     * Normal shutdown, never treat as error.
     * Used during intentional process termination.
     */
    NORMAL,
  }

  @Volatile
  internal var myExitPolicy: ProcessExitPolicy = CHECK_CODE

  companion object {
    fun create(
      parentScope: ParentOfIjentScopes,
      process: Process,
      ijentLabel: String,
      isExpectedProcessExit: suspend (exitCode: Int) -> Boolean = { it == 0 },
    ): IjentSessionProcessMediator {
      val ijentProcessScope = IjentSessionMediatorUtils.createProcessScope(parentScope, ijentLabel, LOG)
      return create(
        parentScope,
        ijentProcessScope,
        JavaProcessFacade(ijentProcessScope, process),
        ijentLabel,
        isExpectedProcessExit,
      )
    }

    /**
     * See the docs of [IjentSessionProcessMediator].
     *
     * [ijentLabel] is used only for logging.
     *
     * Beware that [parentScope] receives [IjentUnavailableException.CommunicationFailure] if IJent _suddenly_ exits, f.i., after SIGKILL.
     * Nothing happens with [parentScope] if IJent exits expectedly, f.i., after [com.intellij.platform.ijent.IjentApi.close].
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun create(
      parentScope: ParentOfIjentScopes,
      ijentProcessScope: IjentScope,
      process: ProcessFacade,
      ijentLabel: String,
      isExpectedProcessExit: suspend (exitCode: Int) -> Boolean = { it == 0 },
    ): IjentSessionProcessMediator {
      val context = IjentThreadPool.coroutineContext

      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 30,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(IjentThreadPool.coroutineContext + ijentProcessScope.s.coroutineNameAppended("stderr logger")) {
        IjentSessionMediatorUtils.ijentProcessStderrLogger(process.stderr, ijentLabel, lastStderrMessages, LOG)
      }

      val mediator = IjentSessionProcessMediator(ijentProcessScope, process)

      val awaiterScope = ijentProcessScope.s.launch(context = context + ijentProcessScope.s.coroutineNameAppended("exit awaiter scope")) {
        @Suppress("checkedExceptions") val exitCode = process.exitCode.await()
        LOG.debug { "IJent process $ijentLabel exited with code $exitCode" }
        IjentSessionMediatorUtils.ijentProcessExitCodeHandler(
          ijentLabel,
          lastStderrMessages,
          LOG,
          exitCode,
          isExpectedProcessExit(exitCode),
        )
      }

      val finalizerScope = ijentProcessScope.s.launch(context = context + ijentProcessScope.s.coroutineNameAppended("finalizer scope")) {
        IjentSessionMediatorUtils.ijentProcessFinalizer(ijentLabel) { ijentProcessFinalizer(ijentLabel, mediator) }
      }

      awaiterScope.invokeOnCompletion { err ->
        finalizerScope.cancel(if (err != null) CancellationException(err.message, err) else null)
      }

      return mediator
    }
  }
}

private suspend fun ijentProcessFinalizer(ijentLabel: String, mediator: IjentSessionProcessMediator) {
  mediator.myExitPolicy = NORMAL
  val process = mediator.process

  if (!process.isAlive) return

  try {
    LOG.debug { "Closing stdin of $ijentLabel" }
    runCatching { process.stdin.close(null) }

    if (SystemInfoRt.isWindows) {
      awaitProcessExit(process, 1.5.seconds)
      if (!process.isAlive) return
    }

    process.destroy()

    awaitProcessExit(process, 1.5.seconds)

    if (process.isAlive) {
      LOG.warn("The process $ijentLabel is still alive, it will be killed")
      process.destroyForcibly()
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Failed to terminate $ijentLabel", e)
  }
}

private suspend fun awaitProcessExit(process: IjentSessionProcessMediator.ProcessFacade, timeout: Duration) {
  val deadline = System.nanoTime() + timeout.inWholeNanoseconds
  while (process.isAlive && System.nanoTime() < deadline) {
    delay(50.milliseconds)
  }
}

private val LOG = IjentLog.getInstance<IjentSessionProcessMediator>()