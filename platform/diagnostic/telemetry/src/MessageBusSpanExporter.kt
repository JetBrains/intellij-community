// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry


import com.intellij.diagnostic.telemetry.TelemetryReceivedListener.Companion.TOPIC
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CoroutineScope

class MessageBusSpanExporter : AsyncSpanExporter {
  companion object {
    private val lock = Object()
    private val spansData = mutableListOf<SpanData>()

    @Volatile
    private var syncPublisher: TelemetryReceivedListener? = null
    private fun initPublisher() = ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC)
  }
  internal class OpenTelemetryApplicationInitializedListener : ApplicationInitializedListener {
    override suspend fun execute(asyncScope: CoroutineScope) {
      val dataToSend = mutableListOf<SpanData>()
      synchronized(lock) {
        val publisher = initPublisher()
        syncPublisher = publisher
        dataToSend.addAll(spansData)
        spansData.clear()
        publisher
      }.sendSpans(dataToSend)
    }
  }

  override suspend fun export(spans: Collection<SpanData>) {
    getSyncPublisher()?.sendSpans(spans) ?: synchronized(lock) {
      spansData.addAll(spans)
    }
  }

  private fun getSyncPublisher(): TelemetryReceivedListener? {
    if (syncPublisher == null) {
      synchronized(lock) {
        if (syncPublisher == null) {
          syncPublisher = initPublisher()
        }
      }
    }
    return syncPublisher
  }
}