// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.*
import com.intellij.platform.ijent.IjentApplicationScope
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.awaitExit
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * A wrapper for a [Process] that runs IJent. The wrapper logs stderr lines, waits for the exit code, terminates the process in case
 * of problems in the IDE.
 */
class IjentSessionMediator private constructor(val scope: CoroutineScope, val process: Process) {
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
    /** See the docs of [IjentSessionMediator] */
    @OptIn(DelicateCoroutinesApi::class)
    fun create(process: Process, ijentId: IjentId): IjentSessionMediator {
      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 0,
        extraBufferCapacity = 30,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      val connectionScope = IjentApplicationScope.instance().childScope("ijent $ijentId > connection scope", supervisor = false)

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(blockingDispatcher + CoroutineName("ijent $ijentId > stderr logger")) {
        ijentProcessStderrLogger(process, ijentId, lastStderrMessages)
      }

      val mediator = IjentSessionMediator(connectionScope, process)

      val awaiterScope = IjentApplicationScope.instance().launch(CoroutineName("ijent $ijentId > exit awaiter scope")) {
        ijentProcessExitAwaiter(ijentId, mediator, lastStderrMessages)
      }

      val finalizerScope = connectionScope.launch(CoroutineName("ijent $ijentId > finalizer scope")) {
        ijentProcessFinalizer(ijentId, mediator)
      }

      awaiterScope.invokeOnCompletion {
        finalizerScope.cancel()
      }

      finalizerScope.invokeOnCompletion {
        connectionScope.cancel()
      }

      return mediator
    }

    @VisibleForTesting
    val lastStderrMessagesTimeout = 5.seconds // A random timeout.
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

@OptIn(DelicateCoroutinesApi::class)
private suspend fun ijentProcessExitAwaiter(
  ijentId: IjentId,
  mediator: IjentSessionMediator,
  lastStderrMessages: MutableSharedFlow<String?>,
) {
  val exitCode = mediator.process.awaitExit()
  LOG.debug { "IJent process $ijentId exited with code $exitCode" }

  val isExitExpected = when (mediator.expectedErrorCode) {
    IjentSessionMediator.ExpectedErrorCode.NO -> false
    IjentSessionMediator.ExpectedErrorCode.ZERO -> exitCode == 0
    IjentSessionMediator.ExpectedErrorCode.ANY -> true
  }

  if (!isExitExpected) {
    // This coroutine must be bound to something that outlives `coroutineScope`, in order to not block its cancellation and
    // to not truncate the last lines of the logs, which are usually the most important.
    GlobalScope.launch {
      val stderr = StringBuilder()
      try {
        withTimeout(IjentSessionMediator.lastStderrMessagesTimeout) {
          collectLines(lastStderrMessages, stderr)
        }
      }
      finally {
        // There's `LOG.error(message, Attachment)`, but it doesn't work well with `LoggedErrorProcessor.executeAndReturnLoggedError`.
        LOG.error(RuntimeExceptionWithAttachments(
          "The process $ijentId suddenly exited with the code $exitCode",
          Attachment("stderr", stderr.toString()),
        ))
      }
    }
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
private suspend fun ijentProcessFinalizer(ijentId: IjentId, mediator: IjentSessionMediator) {
  try {
    awaitCancellation()
  }
  catch (err: Exception) {
    LOG.debug(err) { "$ijentId is going to be terminated due to receiving an error" }
    throw err
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