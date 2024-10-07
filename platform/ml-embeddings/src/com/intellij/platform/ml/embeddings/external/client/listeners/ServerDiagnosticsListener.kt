// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.client.listeners

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import okio.withLock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock

class ServerDiagnosticsListener : ProcessListener {
  private val logsHistory = LinkedBlockingQueue<String>()
  private val lock = ReentrantLock()

  override fun startNotified(event: ProcessEvent) {
    val message = "Embeddings native server started"
    thisLogger().info(message)
    updateHistory(message)
  }

  override fun processTerminated(event: ProcessEvent) {
    val message = "Embeddings native server terminated with status code ${event.exitCode}"
    thisLogger().info(message)
    updateHistory(message)
  }

  override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
    val message = "Embeddings native server will terminate (destroyed: $willBeDestroyed)"
    thisLogger().info(message)
    updateHistory(message)
  }

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    val message = event.text
    if (ProcessOutputType.isStderr(outputType)) thisLogger().warn(message) else thisLogger().debug(message)
    updateHistory(message)
  }

  fun grpcChannelStateChanged(connectivityState: String) {
    val message = "New GRPC channel state is $connectivityState"
    thisLogger().debug(message)
    updateHistory(message)
  }

  fun connectionAddress(hostname: String, port: Int) {
    val message = "Embeddings native server address is $hostname:$port"
    thisLogger().info(message)
    updateHistory(message)
  }

  @Suppress("unused")
  fun getLogsAttachment(): Attachment = Attachment("inference_service.log", logsHistory.asIterable().joinToString(separator = "\n"))

  private fun updateHistory(message: String) = lock.withLock {
    while (logsHistory.size > MAX_HISTORY_SIZE) {
      logsHistory.poll()
    }
    logsHistory.put(message)
  }

  companion object {
    const val MAX_HISTORY_SIZE: Int = 1_000
  }
}