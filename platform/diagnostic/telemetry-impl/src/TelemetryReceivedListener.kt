// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.util.messages.Topic
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * Listener for open telemetry data (spans) being sent from backend to client side after backend initialisation and controller connection
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface TelemetryReceivedListener : EventListener {
  companion object {
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<TelemetryReceivedListener> = Topic(TelemetryReceivedListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
  fun sendSpans(spanData: Collection<SpanData>)
}