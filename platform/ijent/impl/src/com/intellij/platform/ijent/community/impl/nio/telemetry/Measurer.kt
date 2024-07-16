// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.telemetry

import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.metrics.LongHistogram
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.measureTime

private val eventsCounter: AtomicLong = AtomicLong()

internal val ijentMetricsScope = Scope("ijent", PlatformMetrics)
internal val ijentMeter = TelemetryManager.getMeter(ijentMetricsScope)

@Suppress("Unused")
internal val eventsCounterMeter = ijentMeter.counterBuilder("ijent.events.count").buildObserver().also {
  ijentMeter.batchCallback(
    { it.record(eventsCounter.get()) },
    it
  )
}

// TODO: probably should avoid initialization of ~ 30 histograms each with ~ 10K elements in production (use special flag?)
private val histograms: Map<Measurer.Operation, LongHistogram> = Measurer.Operation.entries.associate { it to initHistogram(it) }


/**
 * Will generate around 10K buckets with the following logic.
 *
 * - from 0 ns to 50M ns (50 ms) - granularity is 50K ns (50 us)
 * - from 50M ns (50 ms) to 500M ns (500 ms = 0.5s) - granularity is 100K ns (100 us = 0.1 ms)
 * - from 500M ns (500 ms) to 5_000M ns (5s) - granularity is 1_000K ns (1000 us = 1 ms)
 * - from 5 sec to 120 sec, the granularity is power of 2, starting from the 33nd power (2^33)
 */
private fun generateHistogramBucketsBoundaries(): List<Double> {
  val powerOfTwoBuckets = mutableListOf<Double>()

  var generatedBoundary = 0.0
  var power = 33.0
  do {
    generatedBoundary = Math.pow(2.0, power++)
    powerOfTwoBuckets.add(generatedBoundary)
  }
  while (generatedBoundary <= 120_000_000_000)

  return (0..50_000_000 step 50_000) // 50K ns (50 us) granularity
    .plus(50_100_000..<500_000_000 step 100_000) // 100K ns (100 us) granularity
    .plus(501_000_000..5_000_000_000 step 1_000_000) // 1_000K ns (1 ms) granularity
    .plus(powerOfTwoBuckets)
    .map { it.toDouble() }
}

private fun initHistogram(operation: Measurer.Operation): LongHistogram = ijentMeter.histogramBuilder("ijent.${operation.name}")
  .setExplicitBucketBoundariesAdvice(generateHistogramBucketsBoundaries())
  .setUnit("ns")
  .ofLongs()
  .build()

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
  val result: Result<T>
  val time = measureTime {
    result = runCatching {
      body()
    }
  }
  when (result.exceptionOrNull()) {
    null, is IOException -> {
      eventsCounter.incrementAndGet()
      histograms[operation]?.record(time.inWholeNanoseconds)
    }
  }
  return result.getOrThrow()
}