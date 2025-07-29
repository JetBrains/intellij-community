// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.Cancellation.ensureActive
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ijent.IjentLogger
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.coroutineNameAppended
import com.intellij.platform.ijent.spi.IjentSessionMediator.ProcessExitPolicy.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.ContainerUtil
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

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
abstract class IjentSessionMediator private constructor(
  val ijentProcessScope: CoroutineScope,
  val process: Process,
  val processExit: Deferred<Unit>,
) {
  /**
   * Defines how process exits should be handled in terms of error reporting.
   * Used to determine whether a process termination should be treated as an error.
   */
  enum class ProcessExitPolicy {
    /**
     * Treat any exit as an error.
     * Used during initialization when process must stay alive.
     */
    ERROR,

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
  internal var myExitPolicy: ProcessExitPolicy = ERROR

  companion object {
    /**
     * See the docs of [IjentSessionMediator].
     *
     * [ijentLabel] is used only for logging.
     *
     * Beware that [parentScope] receives [IjentUnavailableException.CommunicationFailure] if IJent _suddenly_ exits, f.i., after SIGKILL.
     * Nothing happens with [parentScope] if IJent exits expectedly, f.i., after [com.intellij.platform.ijent.IjentApi.close].
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun create(parentScope: CoroutineScope, process: Process, ijentLabel: String, isExpectedProcessExit: suspend (exitCode: Int) -> Boolean = { it == 0 }): IjentSessionMediator {
      require(parentScope.coroutineContext[Job] != null) {
        "Scope $parentScope has no Job"
      }
      val context = IjentThreadPool.asCoroutineDispatcher()
      val ijentProcessScope = run {
        // Prevents from logging the error by the default exception handler.
        // Errors are logged explicitly in this function.
        val dummyExceptionHandler = CoroutineExceptionHandler { _, err -> /* nothing */ }

        // This supervisor scope exists only to prevent automatic propagation of IjentUnavailableException to the parent scope.
        // Instead, there's a logic below that decides if a specific IjentUnavailableException should be propagated to the parent scope.
        val trickySupervisorScope = parentScope.childScope(ijentLabel, context + dummyExceptionHandler, supervisor = true)

        val ijentProcessScope = trickySupervisorScope.childScope(ijentLabel, supervisor = false)

        ijentProcessScope.coroutineContext.job.invokeOnCompletion { err ->
          trickySupervisorScope.cancel()

          if (err != null) {
            val propagateToParentScope = when (err) {
              is IjentUnavailableException -> when (err) {
                is IjentUnavailableException.ClosedByApplication -> false
                is IjentUnavailableException.CommunicationFailure -> !err.exitedExpectedly
              }
              else -> true
            }

            if (propagateToParentScope) {
              try {
                err.addSuppressed(Throwable("Rethrown from here"))
                parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
                  throw err
                }
              }
              catch (_: Throwable) {
                // It seems that the scope has already been canceled with something else.
              }
            }

            // TODO Callers should be able to define their own exception handlers.
            logIjentError(ijentLabel, err)
          }
        }

        ijentProcessScope
      }

      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 30,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(blockingDispatcher + ijentProcessScope.coroutineNameAppended("stderr logger")) {
        ijentProcessStderrLogger(process, ijentLabel, lastStderrMessages)
      }

      val processExit = CompletableDeferred<Unit>()

      val mediator = object : IjentSessionMediator(ijentProcessScope, process, processExit) {
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

private val loggedErrors = Collections.newSetFromMap(ContainerUtil.createConcurrentWeakMap<Throwable, Boolean>())

private fun logIjentError(ijentLabel: String, exception: Throwable) {
  // The logger can create new services, and since this function is called inside an already failed coroutine context,
  // service creation would be impossible without `executeInNonCancelableSection`.
  Cancellation.executeInNonCancelableSection {
    when (exception) {
      is IjentUnavailableException -> when (exception) {
        is IjentUnavailableException.ClosedByApplication -> Unit

        is IjentUnavailableException.CommunicationFailure -> {
          if (!exception.exitedExpectedly && loggedErrors.add(exception)) {
            LOG.error("Exception in connection with IJent $ijentLabel: ${exception.message}", exception)
          }
        }
      }

      is CancellationException -> Unit

      else -> {
        if (loggedErrors.add(exception)) {
          LOG.error("Unexpected error during communnication with IJent $ijentLabel", exception)
        }
      }
    }
  }
}

private suspend fun ijentProcessStderrLogger(process: Process, ijentLabel: String, lastStderrMessages: MutableSharedFlow<String?>) {
  try {
    process.errorStream.reader().useLines { lines ->
      for (line in lines) {
        yield()
        if (line.isNotEmpty()) {
          logIjentStderr(ijentLabel, line)
          lastStderrMessages.emit(line)
        }
      }
    }
  }
  catch (err: IOException) {
    LOG.debug { "$ijentLabel bootstrap got an error: $err" }
  }
  finally {
    lastStderrMessages.emit(null)
  }
}

private val ijentLogMessageRegex = Regex(
  """
^
(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+\S*)
\s+
(\w+)
\s+
(.*)
""",
  RegexOption.COMMENTS,
)

private val logTargets: Map<String, Logger> =
  IjentLogger.ALL_LOGGERS.associateByTo(hashMapOf()) { logger ->
    val getter = JulLogger::class.java.getDeclaredMethod("getLoggerName")
    val oldIsAccessible = getter.isAccessible
    val loggerName = try {
      getter.isAccessible = true
      getter.invoke(logger) as String
    }
    finally {
      getter.isAccessible = oldIsAccessible
    }
    loggerName.removePrefix("#com.intellij.platform.ijent.")
  }

private fun logIjentStderr(ijentLabel: String, line: String) {
  val hostDateTime = ZonedDateTime.now()

  val (rawRemoteDateTime, level, message) =
    ijentLogMessageRegex.matchEntire(line)?.destructured
    ?: run {
      val message = "$ijentLabel log: $line"
      // It's important to always log such messages,
      // but if logs are supposed to be written to a separate file in debug level,
      // they're logged in debug level.
      if (LOG.isDebugEnabled) {
        LOG.debug(message)
      }
      else {
        LOG.info(message)
      }
      return
    }

  val dateTimeDiff = try {
    java.time.Duration.between(ZonedDateTime.parse(rawRemoteDateTime), hostDateTime).toKotlinDuration()
  }
  catch (_: DateTimeParseException) {
    LOG.debug { "$ijentLabel log: $line" }
    return
  }

  val logger: ((String) -> Unit) = run {
    val logTargetPrefix = message
      .take(256)  // I hope that there will never be a span/target name longer than 256 characters.
      .split("ijent::#", limit = 2)
      .getOrNull(1)
      ?.substringBefore("::")
      ?.takeWhile { it.isLetter() || it == '_' }

    val logger = logTargets[logTargetPrefix] ?: IjentLogger.OTHER_LOG

    when (level) {
      "TRACE" -> if (logger.isTraceEnabled) logger::trace else return
      "INFO" -> logger::info
      "WARN" -> logger::warn
      "ERROR" -> logger::error
      "DEBUG" -> logger::debug
      else -> if (logger.isDebugEnabled) logger::debug else return
    }
  }

  logger(buildString {
    append(ijentLabel)
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
  ijentLabel: String,
  mediator: IjentSessionMediator,
  lastStderrMessages: MutableSharedFlow<String?>,
): Nothing {
  while (!mediator.process.waitFor(1, TimeUnit.SECONDS)) {
    ensureActive()
  }
  val exitCode = mediator.process.exitValue()
  LOG.debug { "IJent process $ijentLabel exited with code $exitCode" }

  val isExitExpected = when (mediator.myExitPolicy) {
    ERROR -> false
    CHECK_CODE -> mediator.isExpectedProcessExit(exitCode)
    NORMAL -> true
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
      "The process $ijentLabel suddenly exited with the code $exitCode",
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
private suspend fun ijentProcessFinalizer(ijentLabel: String, mediator: IjentSessionMediator): Nothing {
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

private val LOG = logger<IjentSessionMediator>()