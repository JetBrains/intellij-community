package org.jetbrains.bazel.jvm.logging

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.io.PrintStream
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class LogEvent(
  @JvmField val timestamp: Instant = Instant.now(),
  @JvmField val message: String? = null,
  @JvmField val messageTemplate: String? = null,
  @JvmField val level: String? = null,
  @JvmField val exception: Throwable? = null,
  @JvmField val context: Array<Any>? = null,
)

interface LogRenderer {
  fun createStringBuilder(): StringBuilder

  fun render(sb: StringBuilder, event: LogEvent)
}

class LogWriter(
  coroutineScope: CoroutineScope,
  private val writer: PrintStream,
  private val closeWriterOnShutdown: Boolean = false,
  private val renderer: LogRenderer = JsonLogRenderer,
) {
  private val logChannel = Channel<LogEvent>(Channel.UNLIMITED)

  // NonCancellable - make sure, that we process all messages. To stop processing, close the channel.
  private val job = coroutineScope.launch(NonCancellable) { processQueue() }

  fun log(event: LogEvent) {
    val sendStatus = logChannel.trySend(event)
    require(sendStatus.isSuccess) { "Cannot log: $sendStatus" }
  }

  fun info(message: String, context: Array<Any>) {
    log(LogEvent(message = message, context = context))
  }

  fun info(message: String, exception: Throwable? = null) {
    log(LogEvent(message = message, exception = exception))
  }

  suspend fun shutdown() {
    try {
      logChannel.close()
      job.join()
    }
    finally {
      if (closeWriterOnShutdown) {
        writer.close()
      }
      else {
        writer.flush()
      }
    }
  }

  private suspend fun processQueue() {
    coroutineScope {
      val flushRequestChannel = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      val flushJob = launch {
        for (ignored in flushRequestChannel) {
          delay(10.seconds)
          withContext(Dispatchers.IO) {
            writer.flush()
          }
        }
      }

      val sb = renderer.createStringBuilder()
      for (event in logChannel) {
        renderer.render(sb, event)
        withContext(Dispatchers.IO) {
          writer.append(sb)
        }
        flushRequestChannel.trySend(Unit)
      }
      flushRequestChannel.close()
      flushJob.join()
    }
  }
}