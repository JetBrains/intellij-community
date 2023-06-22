// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.impl.TelemetryReceivedListener.Companion.TOPIC
import com.intellij.openapi.application.ApplicationManager
import io.opentelemetry.sdk.trace.data.SpanData

class MessageBusSpanExporter : AsyncSpanExporter {
  companion object {
    private val lock = Object()
    private val spansData = mutableListOf<SpanData>()
    private fun initPublisher() = ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC)
  }

  override suspend fun export(spans: Collection<SpanData>) {
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
      val dataToSend = mutableListOf<SpanData>()
      synchronized(lock) {
        val publisher = initPublisher()
        if (!spansData.isEmpty()) dataToSend.addAll(spansData)
        dataToSend.addAll(spans)
        spansData.clear()
        publisher
      }.sendSpans(dataToSend)
    }
    else {
      synchronized(lock) {
        spansData.addAll(spans)
      }
    }
  }
}