package com.intellij.platform.lsp.impl.logging

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val LOG_SIZE_LIMIT_CHARS = 100_000_000
private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private val LOG = logger<LanguageServiceLoggerService>()

/**
 * @see LanguageServiceLoggerService
 */
@OptIn(FlowPreview::class)
class LanguageServiceLogger internal constructor(
  internal val logPath: Path,
  cs: CoroutineScope,
  private val writeStandardLogIfLimitReached: Boolean,
) {

  private val flushRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val flushRequestJob: Job
  private var logSizeChars: Int = 0

  private val writer: PrintWriter by lazy {
    PrintWriter(BufferedOutputStream(
      Files.newOutputStream(logPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))
  }

  init {
    flushRequestJob = cs.launch {
      flushRequests.sample(1.seconds).debounce(100.milliseconds).collect {
        writer.flush()
      }
    }
  }

  private fun isLogSizeLimitReached(): Boolean = logSizeChars >= LOG_SIZE_LIMIT_CHARS

  fun logInbound(message: CharSequence): Unit = doLog("IN ", message, ::logInboundToIdeaLog)
  fun logOutbound(message: CharSequence): Unit = doLog("OUT", message, ::logOutboundToIdeaLog)
  fun logError(message: CharSequence): Unit = doLog("ERR", message, ::logErrorToIdeaLog)

  private fun logInboundToIdeaLog(message: CharSequence): Unit = LOG.debug("<-- " + logPath.fileName + ": " + shorten(message))
  private fun logOutboundToIdeaLog(message: CharSequence): Unit = LOG.debug("--> " + logPath.fileName + ": " + shorten(message))
  private fun logErrorToIdeaLog(message: CharSequence): Unit = LOG.debug("<xx " + logPath.fileName + ": " + shorten(message))
  private fun shorten(message: CharSequence): String = StringUtil.shortenTextWithEllipsis(message.toString(), 3000, 500)

  private fun doLog(channelTag: String, message: CharSequence, logToIdeaLog: (CharSequence) -> Unit) {
    if (isLogSizeLimitReached() && writeStandardLogIfLimitReached ||
        Registry.`is`("lsp.communication.standard.log.file", false)) {
      logToIdeaLog(message)
    }

    if (isLogSizeLimitReached()) return

    logSizeChars += message.length

    for (line in message.lineSequence()) {
      if (line.isNotEmpty()) {
        writer.write(formatCurrentTime() + ' ' + channelTag + ' ' + line + "\n")
      }
    }

    if (isLogSizeLimitReached()) {
      writer.write("LOG SIZE LIMIT REACHED\n")
      close()
      return
    }

    check(flushRequests.tryEmit(Unit))
  }

  private fun formatCurrentTime(): String = TIMESTAMP_FORMAT.format(millisToDate(System.currentTimeMillis()))

  internal fun close() {
    flushRequestJob.cancel()
    writer.close()
  }
}
