// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal class AsyncFileLogger(file: Path, coroutineScope: CoroutineScope) {
  private val logChannel = Channel<String>(Channel.UNLIMITED)
  private val writer = Files.newOutputStream(file, StandardOpenOption.APPEND, StandardOpenOption.CREATE).bufferedWriter()

  // NonCancellable - make sure, that we process all messages. To stop processing, close the channel.
  private val job = coroutineScope.launch(Dispatchers.IO + NonCancellable) { processQueue() }

  fun log(message: String) {
    val sendStatus = logChannel.trySend(formatMessage(message))
    require(sendStatus.isSuccess) { "Cannot log: $sendStatus" }
  }

  suspend fun shutdown() {
    try {
      logChannel.close()
      job.join()

      writer.appendLine(formatMessage("logger shutdown"))
    }
    finally {
      writer.close()
    }
  }

  /**
   * Processes the log queue and writes messages to the file.
   */
  private suspend fun processQueue() {
    for (message in logChannel) {
      writer.appendLine(message)
      writer.flush()
    }
  }
}

private fun formatMessage(message: String): String = "[${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}] $message"
