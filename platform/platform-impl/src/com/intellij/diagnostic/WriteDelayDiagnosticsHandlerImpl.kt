// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.application.WriteDelayDiagnostics.WriteDelayDiagnosticsHandler
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.TelemetryManager

internal class WriteDelayDiagnosticsHandlerImpl: WriteDelayDiagnosticsHandler {
  private var count: Long = 0
  private var totalTime: Long = 0
  private var maxTime: Long = 0
  private val delaysMap: MutableMap<Long,Long> = mutableMapOf()

  init{
    val meter = TelemetryManager.getInstance().getMeter(PlatformMetrics)
    val countObservableCounter = meter.counterBuilder("writeAction.count")
      .setDescription("Number of write actions run")
      .buildObserver()
    val timeObservableCounter = meter.counterBuilder("writeAction.wait.ms")
      .setDescription("Total time waiting for the write lock")
      .setUnit("ms")
      .buildObserver()
    val maxTimeObservableGauge = meter.gaugeBuilder("writeAction.max.wait.ms")
      .ofLongs()
      .setDescription("Max time waiting for the write lock")
      .setUnit("ms")
      .buildObserver()
    val medianTimeObservableGauge = meter.gaugeBuilder("writeAction.median.wait.ms")
      .ofLongs()
      .setDescription("Median time waiting for the write lock, step 100 ms")
      .setUnit("ms")
      .buildObserver()
    meter.batchCallback(
      {
      synchronized(this){
        countObservableCounter.record(count)
        timeObservableCounter.record(totalTime)
        maxTimeObservableGauge.record(maxTime)

        var currentCount: Long = 0
        var medianValue: Long = 0
        delaysMap.entries.asSequence()
          .sortedBy { it.key }
          .takeWhile { currentCount < count / 2 }
          .forEach {
            medianValue = it.key * 100
            currentCount += it.value
          }
        medianTimeObservableGauge.record(medianValue)
      }
    }, countObservableCounter, timeObservableCounter, maxTimeObservableGauge, medianTimeObservableGauge)

  }

  override fun registerWrite(waitTime: Long) {
    synchronized(this){
      count++
      totalTime += waitTime
      if( maxTime < waitTime){
        maxTime = waitTime
      }
      val timeBucket = (totalTime/100)
      delaysMap[timeBucket] = delaysMap.getOrDefault(timeBucket, 0) + 1
    }
  }
}