// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.Cancellation.ensureActive
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.coroutineNameAppended
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.CHECK_CODE
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.NORMAL
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import java.util.concurrent.TimeUnit
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
abstract class IjentSessionProcessMediator private constructor(
  override val ijentProcessScope: CoroutineScope,
  val process: Process,
  override val processExit: Deferred<Unit>,
): IjentSessionMediator {
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

  internal abstract suspend fun isExpectedProcessExit(exitCode: Int): Boolean

  @Volatile
  internal var myExitPolicy: ProcessExitPolicy = CHECK_CODE

  companion object {
    /**
     * See the docs of [IjentSessionProcessMediator].
     *
     * [ijentLabel] is used only for logging.
     *
     * Beware that [parentScope] receives [IjentUnavailableException.CommunicationFailure] if IJent _suddenly_ exits, f.i., after SIGKILL.
     * Nothing happens with [parentScope] if IJent exits expectedly, f.i., after [com.intellij.platform.ijent.IjentApi.close].
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun create(parentScope: CoroutineScope, process: Process, ijentLabel: String, isExpectedProcessExit: suspend (exitCode: Int) -> Boolean = { it == 0 }): IjentSessionProcessMediator {
      require(parentScope.coroutineContext[Job] != null) {
        "Scope $parentScope has no Job"
      }
      val context = IjentThreadPool.coroutineContext
      val ijentProcessScope = IjentSessionMediatorUtils.createProcessScope(parentScope, ijentLabel, LOG)

      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 30,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(IjentThreadPool.coroutineContext + ijentProcessScope.coroutineNameAppended("stderr logger")) {
        IjentSessionMediatorUtils.ijentProcessStderrLogger(process.errorStream, ijentLabel, lastStderrMessages, LOG)
      }

      val processExit = CompletableDeferred<Unit>()

      val mediator = object : IjentSessionProcessMediator(ijentProcessScope, process, processExit) {
        override suspend fun isExpectedProcessExit(exitCode: Int): Boolean = isExpectedProcessExit(exitCode)
      }

      val awaiterScope = ijentProcessScope.launch(context = context + ijentProcessScope.coroutineNameAppended("exit awaiter scope")) {
        ijentProcessExitAwaiter(ijentLabel, mediator, lastStderrMessages)
      }

      val finalizerScope = ijentProcessScope.launch(context = context + ijentProcessScope.coroutineNameAppended("finalizer scope")) {
        ijentProcessFinalizer(ijentLabel, mediator)
      }

      awaiterScope.invokeOnCompletion { err ->
        processExit.complete(Unit)
        finalizerScope.cancel(if (err != null) CancellationException(err.message, err) else null)
      }

      return mediator
    }
  }
}

private suspend fun ijentProcessExitAwaiter(
  ijentLabel: String,
  mediator: IjentSessionProcessMediator,
  lastStderrMessages: MutableSharedFlow<String?>,
): Nothing {
  while (!mediator.process.waitFor(1, TimeUnit.SECONDS)) {
    ensureActive()
  }
  val exitCode = mediator.process.exitValue()
  LOG.debug { "IJent process $ijentLabel exited with code $exitCode" }

  val isExitExpected = when (mediator.myExitPolicy) {
    CHECK_CODE -> mediator.isExpectedProcessExit(exitCode)
    NORMAL -> true
  }

  val error = if (isExitExpected) {
    IjentUnavailableException.CommunicationFailure("IJent process exited successfully").apply { exitedExpectedly = true }
  }
  else {
    val stderr = StringBuilder()
    // This code blocks the whole coroutine scope, so it should
    withContext(NonCancellable) {
      val timeoutResult: Unit? = withTimeoutOrNull(1.seconds) {
        collectLines(lastStderrMessages, stderr)
      }
      if (timeoutResult == null) {
        stderr.append("\n<didn't collect the whole stderr>")
      }
    }
    IjentUnavailableException.CommunicationFailure(
      "The process $ijentLabel suddenly exited with the code $exitCode",
      Attachment("stderr", stderr.toString()),
    )
  }

  // TODO IJPL-198706 When IJent unexpectedly terminates, users should be asked for further actions.
  if (isExitExpected) {
    LOG.info(error)
  }
  else {
    LOG.warn(error)
  }
  throw error
}

private suspend fun collectLines(lastStderrMessages: SharedFlow<String?>, stderr: StringBuilder) {
  lastStderrMessages
    .takeWhile { it != null }
    .filterNotNull()
    .collect { msg ->
      stderr.append(msg)
      stderr.append("\n")
    }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun ijentProcessFinalizer(ijentLabel: String, mediator: IjentSessionProcessMediator): Nothing {
  try {
    awaitCancellation()
  }
  catch (err: Exception) {
    val actualErrors = generateSequence(err, Throwable::cause).filterTo(mutableListOf()) { it !is CancellationException }

    val existingIjentUnavailableException = actualErrors.filterIsInstance<IjentUnavailableException>().firstOrNull()
    if (existingIjentUnavailableException != null) {
      throw existingIjentUnavailableException
    }

    val cause = actualErrors.firstOrNull() ?: err
    val message =
      if (cause is CancellationException) "The coroutine scope of $ijentLabel was cancelled"
      else "IJent communication terminated due to an error"
    throw IjentUnavailableException.ClosedByApplication(message, cause)
  }
  finally {
    mediator.myExitPolicy = NORMAL
    val process = mediator.process
    if (process.isAlive) {
      GlobalScope.launch(Dispatchers.IO + CoroutineName("$ijentLabel destruction")) {
        try {
          process.waitFor(5, TimeUnit.SECONDS)  // A random timeout.
        }
        finally {
          if (process.isAlive) {
            LOG.warn("The process $ijentLabel is still alive, it will be killed")
            process.destroy()
          }
        }
      }
      GlobalScope.launch(Dispatchers.IO) {
        LOG.debug { "Closing stdin of $ijentLabel" }
        process.outputStream.close()
      }
    }
  }
}

private val LOG = logger<IjentSessionProcessMediator>()