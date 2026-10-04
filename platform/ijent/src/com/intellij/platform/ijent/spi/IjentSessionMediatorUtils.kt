// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ijent.spi

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.PeekableEelReceiveChannel
import com.intellij.platform.eel.channels.readLine
import com.intellij.platform.eel.channels.readUntil
import com.intellij.platform.eel.channels.useLines
import com.intellij.platform.ijent.IjentLog
import com.intellij.platform.ijent.IjentLogger
import com.intellij.platform.ijent.IjentScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.US_ASCII
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@ApiStatus.Internal
object IjentSessionMediatorUtils {
  suspend fun ijentProcessStderrLogger(
    errorStream: EelReceiveChannel,
    ijentLabel: String,
    lastStderrMessages: MutableSharedFlow<String?>,
  ) {
    val lineConsumer = createIjentStderrLineConsumer(ijentLabel, lastStderrMessages)
    try {
      // `useLines` reads the stderr stream with a blocking call that parks its thread for the whole IJent
      // session. Keep that thread on `IjentThreadPool` instead of the default `Dispatchers.IO`, otherwise a
      // `DefaultDispatcher-worker-*` thread (not whitelisted by `ThreadLeakTracker`) is reported as a leak.
      errorStream.useLines(IjentThreadPool.coroutineContext) { lines ->
        for (line in lines) {
          yield()
          lineConsumer.consume(line)
        }
      }
    }
    catch (err: IOException) {
      IjentLogger.LIFETIME_LOG.debug { "$ijentLabel bootstrap got an error: $err" }
    }
    finally {
      lineConsumer.complete()
    }
  }

  fun createIjentStderrLineConsumer(
    ijentLabel: String,
    lastStderrMessages: MutableSharedFlow<String?>,
  ): IjentStderrLineConsumer {
    val logIjentStderr = LogIjentStderr()
    return IjentStderrLineConsumer(lastStderrMessages) { line ->
      logIjentStderr(ijentLabel, line)
    }
  }

  class IjentStderrLineConsumer internal constructor(
    private val lastStderrMessages: MutableSharedFlow<String?>,
    private val lineLogger: (String) -> Unit,
  ) {
    suspend fun consume(line: String) {
      if (line.isNotEmpty()) {
        lineLogger(line)
        lastStderrMessages.emit(line)
      }
    }

    suspend fun complete() {
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

  private val logTargets: Map<String, IjentLog> by lazy {
    IjentLogger.ALL_LOGGERS.mapKeys { (loggerName, _) ->
      loggerName.removePrefix("#com.intellij.platform.ijent.")
    }
  }

  private class LogIjentStderr {
    private var lastLoggingHandler: ((String) -> Unit)? = null

    operator fun invoke(ijentLabel: String, line: String) {
      val hostDateTime = ZonedDateTime.now()

      val (rawRemoteDateTime, level, message) =
        ijentLogMessageRegex.matchEntire(line)?.destructured
        ?: run {
          val message = "$ijentLabel log: $line"
          // Not IJent's own log format — typically raw OpenSSH client output. Map a recognizable OpenSSH
          // level prefix (debug1/2/3, error/fatal, warning) onto the matching IJent level, so that e.g.
          // `debug1: ...` chatter is no longer logged at INFO. Remember the resolved handler so that a
          // following continuation line (which carries no prefix of its own) keeps the same level.
          // It's important to always log such messages; unrecognized lines honor an earlier debug-only
          // routing when present, otherwise default to INFO so they always stay visible.
          val opensshHandler = opensshLevelHandler(line)
          if (opensshHandler != null) {
            lastLoggingHandler = opensshHandler
          }
          val logger = opensshHandler ?: lastLoggingHandler ?: IjentLogger.OTHER_LOG::info
          logger(message)
          return
        }

      val dateTimeDiff = try {
        java.time.Duration.between(hostDateTime, ZonedDateTime.parse(rawRemoteDateTime)).toKotlinDuration()
      }
      catch (_: DateTimeParseException) {
        val logger = lastLoggingHandler ?: IjentLogger.OTHER_LOG::info
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

    /**
     * Maps a raw OpenSSH stderr line onto the matching [logger] handler, or `null` when the line carries no
     * recognizable OpenSSH level tag (so the caller keeps its default handling). The level classification
     * itself lives in the pure, unit-testable [classifyOpensshStderrLine].
     */
    private fun opensshLevelHandler(line: String): ((String) -> Unit)? =
      when (classifyOpensshStderrLine(line)) {
        OpensshStderrLevel.DEBUG -> IjentLogger.LIFETIME_LOG::debug
        OpensshStderrLevel.WARN -> IjentLogger.LIFETIME_LOG::warn
        OpensshStderrLevel.ERROR -> IjentLogger.LIFETIME_LOG::error
        null -> null
      }
  }

  suspend fun ijentProcessExitCodeHandler(
    ijentLabel: String,
    lastStderrMessages: MutableSharedFlow<String?>,
    exitCode: Int,
    isExitExpected: Boolean,
  ): Nothing {
    if (isExitExpected) {
      val error = EelUnavailableException.ClosedByApplication("IJent process exited successfully", null)
      currentCoroutineContext()[IjentScope.Key]?.destroy(error, isRootCause = true)
      IjentLogger.LIFETIME_LOG.debug { error.message }
      // Carrying the domain exception as the cancellation cause makes expected shutdown look like a test failure.
      throw error
    }
    else {
      val error = withContext(NonCancellable) {
        val stderr = StringBuilder()
        val timeoutResult: Unit? = withTimeoutOrNull(1.seconds) {
          collectLines(lastStderrMessages, stderr)
        }
        if (timeoutResult == null) stderr.append("\n<didn't collect the whole stderr>")

        EelUnavailableException.CommunicationFailure(
          "The process $ijentLabel suddenly exited with the code $exitCode",
          null,
        ).also {
          it.addSuppressed(object : Throwable("", null, true, false), ExceptionWithAttachments {
            override fun getAttachments(): Array<out Attachment> {
              return arrayOf(Attachment("stderr", stderr.toString()))
            }
          })
          currentCoroutineContext()[IjentScope.Key]?.destroy(it, isRootCause = true)
        }
      }
      // TODO IJPL-198706 When IJent unexpectedly terminates, users should be asked for further actions.
      IjentLogger.OTHER_LOG.warn(error)
      throw error
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
  suspend fun ijentProcessFinalizer(
    ijentLabel: String,
    mediatorFinalizer: suspend () -> Unit,
  ): Nothing {
    try {
      awaitCancellation()
    }
    catch (err: Exception) {
      val actualErrors = generateSequence(err, Throwable::cause).filterTo(mutableListOf()) { it !is CancellationException }

      val existingIjentUnavailableException = actualErrors.filterIsInstance<EelUnavailableException>().firstOrNull()
      if (existingIjentUnavailableException != null) {
        currentCoroutineContext()[IjentScope.Key]?.destroy(existingIjentUnavailableException, isRootCause = true)
        throw existingIjentUnavailableException
      }

      if (actualErrors.isEmpty()) {
        // A plain cancellation is an application-initiated close; publish the canonical reason but keep the control flow.
        val closed = EelUnavailableException.ClosedByApplication("The coroutine scope of $ijentLabel was cancelled", err)
        currentCoroutineContext()[IjentScope.Key]?.destroy(closed, isRootCause = true)
      }
      // A real failure is not an application close; the exit-code handler publishes the authoritative reason.
      throw err
    }
    finally {
      withContext(NonCancellable) {
        mediatorFinalizer()
      }
    }
  }

  suspend fun PeekableEelReceiveChannel.readLineOrThrow(charset: Charset, msg: String = "Communication terminated unexpectedly"): String {
    var cause: Throwable? = null
    var result: String? = null
    try {
      result = this.readLine(charset)
    }
    catch (err: EelReceiveChannelException) {
      cause = err
    }
    if (result != null) {
      return result
    }
    val error = EelUnavailableException.CommunicationFailure(msg, cause)
    throw error
  }

  suspend fun PeekableEelReceiveChannel.readLineUntilPipeOrThrow(msg: String = "Communication terminated unexpectedly"): String {
    val line = StringBuilder()
    try {
      val pipeReached = readUntil('|'.code.toByte()) { buffer, _ ->
        line.append(US_ASCII.decode(buffer))
      }
      if (!pipeReached) {
        throw EelUnavailableException.CommunicationFailure(msg, null)
      }
    }
    catch (err: EelReceiveChannelException) {
      throw EelUnavailableException.CommunicationFailure(msg, err)
    }
    return line.toString()
  }
}

/**
 * OpenSSH stderr log levels that [IjentSessionMediatorUtils]'s stderr handler recognizes and re-maps onto
 * IJent log levels.
 */
internal enum class OpensshStderrLevel { DEBUG, WARN, ERROR }

/**
 * Best-effort classification of a raw OpenSSH client stderr line into an [OpensshStderrLevel].
 *
 * OpenSSH's `do_log` (log.c) prefixes stderr output with a lowercase level tag: `debug1:`, `debug2:` and
 * `debug3:` are always present, while `error:`/`fatal:` are added when OpenSSH logs through a handler rather
 * than bare stderr. The client additionally emits human-facing `Warning:` notices. Recognizing these tags
 * lets the mediator log OpenSSH's own chatter at DEBUG and its problems at WARN/ERROR instead of dumping
 * everything at INFO.
 *
 * The tag must be a single whitespace-free token ending at the first `": "`, so ordinary sentences that
 * merely contain a colon (e.g. `Connection to host closed: bye`) and progname-prefixed lines without a level
 * tag (e.g. `ssh: Could not resolve host "fakebox"`) are deliberately left unclassified: this returns `null`
 * for them and the caller keeps its default handling.
 */
internal fun classifyOpensshStderrLine(line: String): OpensshStderrLevel? {
  val tag = line.substringBefore(": ", missingDelimiterValue = "")
  if (tag.isEmpty() || tag.any(Char::isWhitespace)) return null
  return when (tag.lowercase()) {
    "debug1", "debug2", "debug3" -> OpensshStderrLevel.DEBUG
    "error", "fatal" -> OpensshStderrLevel.ERROR
    "warning", "warn" -> OpensshStderrLevel.WARN
    else -> null
  }
}
