// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ijent.IjentApplicationScope
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.awaitExit
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * A wrapper for a [Process] that runs IJent. The wrapper logs stderr lines, waits for the exit code, terminates the process in case
 * of problems in the IDE.
 *
 * [processExit] never throws. When it completes, it either means that the process has finished, or that the whole scope of IJent processes
 * is canceled.
 */
class IjentSessionMediator private constructor(val scope: CoroutineScope, val process: Process, val processExit: Deferred<Unit>) {
  enum class ExpectedErrorCode {
    /** During initialization, even a sudden successful exit is an error. */
    NO,

    /** IJent should exit with code 0 only if it has been terminated explicitly from the IDE side. */
    ZERO,

    /** If the process is being destroyed explicitly, on demand, there's no reason to report errors. */
    ANY,
  }

  @Volatile
  var expectedErrorCode = ExpectedErrorCode.NO

  companion object {
    /**
     * See the docs of [IjentSessionMediator].
     *
     * [ijentId] is used only for logging.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun create(process: Process, ijentId: IjentId): IjentSessionMediator {
      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 30,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      val exceptionHandler = IjentSessionCoroutineExceptionHandler(ijentId)
      val connectionScope = IjentApplicationScope.instance().childScope(
        "ijent $ijentId > connection scope",
        supervisor = false,
        context = exceptionHandler + IjentThreadPool.asCoroutineDispatcher(),
      )

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(blockingDispatcher + CoroutineName("ijent $ijentId > stderr logger")) {
        ijentProcessStderrLogger(process, ijentId, lastStderrMessages)
      }

      val processExit = CompletableDeferred<Unit>()

      val mediator = IjentSessionMediator(connectionScope, process, processExit)

      val awaiterScope = IjentApplicationScope.instance().launch(CoroutineName("ijent $ijentId > exit awaiter scope") + exceptionHandler) {
        ijentProcessExitAwaiter(ijentId, mediator, lastStderrMessages)
      }

      val finalizerScope = connectionScope.launch(CoroutineName("ijent $ijentId > finalizer scope")) {
        ijentProcessFinalizer(ijentId, mediator)
      }

      awaiterScope.invokeOnCompletion { err ->
        processExit.complete(Unit)
        finalizerScope.cancel(if (err != null) CancellationException(err.message, err) else null)
      }

      return mediator
    }
  }
}

private class IjentSessionCoroutineExceptionHandler(
  private val ijentId: IjentId,
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
  private val loggedErrors = Collections.newSetFromMap(ContainerUtil.createConcurrentWeakMap<Throwable, Boolean>())

  override fun toString(): String = javaClass.simpleName

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    when (exception) {
      is IjentUnavailableException -> when (exception) {
        is IjentUnavailableException.ClosedByApplication -> Unit

        is IjentUnavailableException.CommunicationFailure -> {
          if (!exception.exitedExpectedly && loggedErrors.add(exception)) {
            LOG.error("Exception in connection with IJent $ijentId: ${exception.message}", exception)
          }
        }
      }

      is CancellationException -> Unit

      else -> {
        if (loggedErrors.add(exception)) {
          LOG.error("Unexpected error during communnication with IJent $ijentId", exception)
        }
      }
    }
  }
}

private suspend fun ijentProcessStderrLogger(process: Process, ijentId: IjentId, lastStderrMessages: MutableSharedFlow<String?>) {
  try {
    process.errorStream.reader().useLines { lines ->
      for (line in lines) {
        yield()
        if (line.isNotEmpty()) {
          logIjentStderr(ijentId, line)
          lastStderrMessages.emit(line)
        }
      }
    }
  }
  catch (err: IOException) {
    LOG.debug { "$ijentId bootstrap got an error: $err" }
  }
  finally {
    lastStderrMessages.emit(null)
  }
}

private val ijentLogMessageRegex = Regex(
  """
(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+\S*)
\s+
(\w+)
\s+
(.*)
""",
  RegexOption.COMMENTS,
)

private fun logIjentStderr(ijentId: IjentId, line: String) {
  val hostDateTime = ZonedDateTime.now()

  val (rawRemoteDateTime, level, message) =
    ijentLogMessageRegex.matchEntire(line)?.destructured
    ?: run {
      LOG.debug { "$ijentId log: $line" }
      return
    }

  val dateTimeDiff = try {
    java.time.Duration.between(ZonedDateTime.parse(rawRemoteDateTime), hostDateTime).toKotlinDuration()
  }
  catch (_: DateTimeParseException) {
    LOG.debug { "$ijentId log: $line" }
    return
  }

  val logger: (String) -> Unit = when (level) {
    "TRACE" -> if (LOG.isTraceEnabled) LOG::trace else return
    "INFO" -> LOG::info
    "WARN" -> LOG::warn
    "ERROR" -> LOG::error
    else -> if (LOG.isDebugEnabled) LOG::debug else return
  }

  logger(buildString {
    append(ijentId)
    append(" log: ")
    if (dateTimeDiff.absoluteValue > 50.milliseconds) {  // The timeout is taken at random.
      append(rawRemoteDateTime)
      append(" (time diff ")
      append(dateTimeDiff)
      append(") ")
    }
    append(message)
  })
}

private suspend fun ijentProcessExitAwaiter(
  ijentId: IjentId,
  mediator: IjentSessionMediator,
  lastStderrMessages: MutableSharedFlow<String?>,
): Nothing {
  val exitCode = mediator.process.awaitExit()
  LOG.debug { "IJent process $ijentId exited with code $exitCode" }

  val isExitExpected = when (mediator.expectedErrorCode) {
    IjentSessionMediator.ExpectedErrorCode.NO -> false
    IjentSessionMediator.ExpectedErrorCode.ZERO -> exitCode == 0
    IjentSessionMediator.ExpectedErrorCode.ANY -> true
  }

  throw if (isExitExpected) {
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
      "The process $ijentId suddenly exited with the code $exitCode",
      Attachment("stderr", stderr.toString()),
    )
  }
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
private suspend fun ijentProcessFinalizer(ijentId: IjentId, mediator: IjentSessionMediator): Nothing {
  try {
    awaitCancellation()
  }
  catch (err: Exception) {
    throw when (val cause = generateSequence(err, Throwable::cause).firstOrNull { it !is CancellationException }) {
      null -> err
      is IjentUnavailableException -> cause
      else -> {
        LOG.debug(err) { "$ijentId is going to be terminated due to receiving an error" }
        IjentUnavailableException.CommunicationFailure("IJent communication terminated due to an error", err)
      }
    }
  }
  finally {
    mediator.expectedErrorCode = IjentSessionMediator.ExpectedErrorCode.ANY
    val process = mediator.process
    if (process.isAlive) {
      GlobalScope.launch(Dispatchers.IO + CoroutineName("$ijentId destruction")) {
        try {
          process.waitFor(5, TimeUnit.SECONDS)  // A random timeout.
        }
        finally {
          if (process.isAlive) {
            LOG.warn("The process $ijentId is still alive, it will be killed")
            process.destroy()
          }
        }
      }
      GlobalScope.launch(Dispatchers.IO) {
        LOG.debug { "Closing stdin of $ijentId" }
        process.outputStream.close()
      }
    }
  }
}

private val LOG = logger<IjentSessionMediator>()