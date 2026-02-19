// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import io.kotest.matchers.longs.shouldBeBetween
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MillisecondsMeasurerTests {

  @Test
  fun `addElapsedTime should add correct time`(): Unit = runBlocking {
    val measurer = MillisecondsMeasurer()
    val startTime = Milliseconds.now()

    delay(500.milliseconds)
    measurer.addElapsedTime(startTime)

    measurer.duration.get().shouldBeBetween(500.milliseconds.inWholeMilliseconds, 1.seconds.inWholeMilliseconds)
    measurer.asMilliseconds().shouldBeBetween(500.milliseconds.inWholeMilliseconds, 1.seconds.inWholeMilliseconds)
  }

  @Test
  fun `multiple invocation of addElapsedTime should be summed up`(): Unit = runBlocking {
    val measurer = MillisecondsMeasurer()
    val startTime = Milliseconds.now()

    delay(100.milliseconds)
    measurer.addElapsedTime(startTime)

    delay(200.milliseconds)
    measurer.addElapsedTime(startTime)

    measurer.duration.get().shouldBeBetween(300.milliseconds.inWholeMilliseconds, 500.milliseconds.inWholeMilliseconds)
    measurer.asMilliseconds().shouldBeBetween(300.milliseconds.inWholeMilliseconds, 500.milliseconds.inWholeMilliseconds)
  }

  @Test
  fun `addMeasuredTime should measure time and return result`() = runBlocking {
    val measurer = MillisecondsMeasurer()

    val result = measurer.addMeasuredTime {
      delay(100.milliseconds)
      "Calculation Result"
    }

    measurer.duration.get().shouldBeBetween(100.milliseconds.inWholeMilliseconds, 150.milliseconds.inWholeMilliseconds)
    measurer.asMilliseconds().shouldBeBetween(100.milliseconds.inWholeMilliseconds, 150.milliseconds.inWholeMilliseconds)
    result.shouldBe("Calculation Result")
  }


  @Test
  fun `multiple invocation of addMeasuredTime should be summed up`() = runBlocking {
    val measurer = MillisecondsMeasurer()

    measurer.addMeasuredTime {
      delay(100.milliseconds)
      "Result"
    }

    measurer.duration.get().shouldBeBetween(100.milliseconds.inWholeMilliseconds, 300.milliseconds.inWholeMilliseconds)

    val result = measurer.addMeasuredTime {
      delay(200.milliseconds)
      "Calculation Result"
    }

    measurer.duration.get().shouldBeBetween(300.milliseconds.inWholeMilliseconds, 500.milliseconds.inWholeMilliseconds)
    measurer.asMilliseconds().shouldBeBetween(300.milliseconds.inWholeMilliseconds, 500.milliseconds.inWholeMilliseconds)

    result.shouldBe("Calculation Result")
  }

  @Test
  fun `addMeasuredTime should measure time of empty block`(): Unit = runBlocking {
    val measurer = MillisecondsMeasurer()

    measurer.addMeasuredTime { }
    measurer.duration.get().shouldBeBetween(0, 10.milliseconds.inWholeMilliseconds)
  }
}