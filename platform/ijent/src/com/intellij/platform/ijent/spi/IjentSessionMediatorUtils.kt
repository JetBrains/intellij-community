// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.Cancellation
import com.intellij.platform.ijent.IjentLogger
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@ApiStatus.Internal
object IjentSessionMediatorUtils {
  private val loggedErrors = Collections.newSetFromMap(ContainerUtil.createConcurrentWeakMap<Throwable, Boolean>())

  fun createProcessScope(parentScope: CoroutineScope, ijentLabel: String, logger: Logger): CoroutineScope {
    val context = IjentThreadPool.coroutineContext
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
        logIjentError(logger, ijentLabel, err)
      }
    }
    return ijentProcessScope
  }

  fun logIjentError(logger: Logger, ijentLabel: String, exception: Throwable) {
    // The logger can create new services, and since this function is called inside an already failed coroutine context,
    // service creation would be impossible without `executeInNonCancelableSection`.
    Cancellation.executeInNonCancelableSection {
      when (exception) {
        is IjentUnavailableException -> when (exception) {
          is IjentUnavailableException.ClosedByApplication -> Unit

          is IjentUnavailableException.CommunicationFailure -> {
            if (!exception.exitedExpectedly && loggedErrors.add(exception)) {
              logger.error("Exception in connection with IJent $ijentLabel: ${exception.message}", exception)
            }
          }
        }

        is CancellationException -> Unit

        else -> {
          if (loggedErrors.add(exception)) {
            logger.error("Unexpected error during communnication with IJent $ijentLabel", exception)
          }
        }
      }
    }
  }

  suspend fun ijentProcessStderrLogger(
    errorStream: InputStream,
    ijentLabel: String,
    lastStderrMessages: MutableSharedFlow<String?>,
    logger: Logger,
  ) {
    try {
      errorStream.reader().useLines { lines ->
        val logIjentStderr = LogIjentStderr(logger)
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
      logger.debug { "$ijentLabel bootstrap got an error: $err" }
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

  private val logTargets: Map<String, Logger> by lazy {
    IjentLogger.ALL_LOGGERS.mapKeys { (loggerName, _) ->
      loggerName.removePrefix("#com.intellij.platform.ijent.")
    }
  }

  private class LogIjentStderr(private val logger: Logger) {
    private var lastLoggingHandler: ((String) -> Unit)? = null

    operator fun invoke(ijentLabel: String, line: String) {
      val hostDateTime = ZonedDateTime.now()

      val (rawRemoteDateTime, level, message) =
        ijentLogMessageRegex.matchEntire(line)?.destructured
        ?: run {
          val message = "$ijentLabel log: $line"
          // It's important to always log such messages,
          // but if logs are supposed to be written to a separate file in debug level,
          // they're logged in debug level.
          val logger = lastLoggingHandler ?: logger::info
          logger(message)
          return
        }

      val dateTimeDiff = try {
        java.time.Duration.between(ZonedDateTime.parse(rawRemoteDateTime), hostDateTime).toKotlinDuration()
      }
      catch (_: DateTimeParseException) {
        val logger = lastLoggingHandler ?: logger::info
        logger(message)
        return
      }

      val logger: ((String) -> Unit)? = run {
        val logTargetPrefix = message
          .take(256)  // I hope that there will never be a span/target name longer than 256 characters.
          .split("ijent::-", limit = 2)
          .getOrNull(1)
          ?.substringBefore("::")
          ?.takeWhile { it.isLetter() || it == '_' }

        val logger = logTargets[logTargetPrefix] ?: IjentLogger.OTHER_LOG

        when (level) {
          "TRACE" -> if (logger.isTraceEnabled) logger::trace else null
          "INFO" -> logger::info
          "WARN" -> logger::warn
          "ERROR" -> logger::error
          "DEBUG" -> if (logger.isDebugEnabled) logger::debug else null
          else -> lastLoggingHandler
        }
      }

      lastLoggingHandler = logger

      if (logger == null) {
        return
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
  }

  suspend fun ijentProcessExitAwaiter(
    ijentLabel: String,
    lastStderrMessages: MutableSharedFlow<String?>,
    logger: Logger,
    isExitExpected: suspend (exitCode: Int) -> Boolean = { it == 0 },
    waitFunction: suspend () -> Int,
  ): Nothing {
    val exitCode = waitFunction()
    logger.debug { "IJent process $ijentLabel exited with code $exitCode" }

    val isExitExpected = isExitExpected(exitCode)

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
      logger.info(error)
    }
    else {
      logger.warn(error)
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
  suspend fun ijentProcessFinalizer(
    ijentLabel: String,
    mediatorFinalizer: () -> Unit,
  ): Nothing {
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
      mediatorFinalizer()

    }
  }
}