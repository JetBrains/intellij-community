// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.platform.diagnostic.telemetry.helpers.use
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong

@ApiStatus.Internal
object Measurer {

  internal val eventsCounter: AtomicLong = AtomicLong()

  internal val ijentMetricsScope = Scope("ijent", PlatformMetrics, verbose = true)
  internal val ijentTracer by lazy { TelemetryManager.getTracer(ijentMetricsScope) }
  internal val ijentMeter by lazy { TelemetryManager.getMeter(ijentMetricsScope) }

  internal val eventsCounterMeter = lazy {
    ijentMeter.counterBuilder("ijent.events.count").buildObserver().also {
      ijentMeter.batchCallback(
        { it.record(eventsCounter.get()) },
        it
      )
    }
  }

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

internal inline fun <T> Measurer.measure(operation: Measurer.Operation, spanNamePrefix: String = "", body: () -> T): T {
  // ensure a lazy value is initialized
  eventsCounterMeter.value
  return ijentTracer.spanBuilder("ijent.${spanNamePrefix.takeIf { it.isEmpty() }?.plus(".")}${operation.name}", TracerLevel.DETAILED).use {
    eventsCounter.incrementAndGet()
    body()
  }
}