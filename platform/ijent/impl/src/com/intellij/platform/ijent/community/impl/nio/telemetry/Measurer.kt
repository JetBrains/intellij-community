// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.telemetry

import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import io.opentelemetry.api.trace.Span
import java.util.concurrent.atomic.AtomicLong

private val eventsCounter: AtomicLong = AtomicLong()

internal val ijentMetricsScope = Scope("ijent", PlatformMetrics)
internal val ijentTracer by lazy { TelemetryManager.getTracer(ijentMetricsScope) }
internal val ijentMeter = TelemetryManager.getMeter(ijentMetricsScope)

@Suppress("Unused")
internal val eventsCounterMeter = ijentMeter.counterBuilder("ijent.events.count").buildObserver().also {
  ijentMeter.batchCallback(
    { it.record(eventsCounter.get()) },
    it
  )
}

object Measurer {
  enum class Operation {
    directoryStreamClose,
    directoryStreamIteratorNext,
    directoryStreamIteratorRemove,
    fileSystemClose,
    fileSystemNewWatchService,
    providerCheckAccess,
    providerCopy,
    providerCreateDirectory,
    providerCreateLink,
    providerCreateSymbolicLink,
    providerDelete,
    providerDeleteIfExists,
    providerGetFileAttributeView,
    providerGetFileStore,
    providerIsHidden,
    providerIsSameFile,
    providerMove,
    providerNewByteChannel,
    providerNewDirectoryStream,
    providerReadAttributes,
    providerReadSymbolicLink,
    providerSetAttribute,
    seekableByteChannelClose,
    seekableByteChannelNewPosition,
    seekableByteChannelPosition,
    seekableByteChannelRead,
    seekableByteChannelSize,
    seekableByteChannelTruncate,
    seekableByteChannelWrite,
    supportedFileAttributeViews;
  }
}

internal inline fun <T> Measurer.measure(operation: Measurer.Operation, body: () -> T): T {
  return ijentTracer.spanBuilder("ijent.${operation.name}").use {
    eventsCounter.incrementAndGet()
    body()
  }
}