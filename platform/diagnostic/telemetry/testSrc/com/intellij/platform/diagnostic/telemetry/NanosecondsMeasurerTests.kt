// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.platform.diagnostic.telemetry.helpers.Nanoseconds
import com.intellij.platform.diagnostic.telemetry.helpers.NanosecondsMeasurer
import io.kotest.matchers.longs.shouldBeBetween
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NanosecondsMeasurerTests {

  @Test
  fun `addElapsedTime should add correct time`(): Unit = runBlocking {
    val measurer = NanosecondsMeasurer()
    val startTime = Nanoseconds.now()

    delay(500.milliseconds)
    measurer.addElapsedTime(startTime)

    measurer.duration.get().shouldBeBetween(500.milliseconds.inWholeNanoseconds, 1.seconds.inWholeNanoseconds)
  }

  @Test
  fun `multiple invocation of addElapsedTime should be summed up`(): Unit = runBlocking {
    val measurer = NanosecondsMeasurer()
    val startTime = Nanoseconds.now()

    delay(100.milliseconds)
    measurer.addElapsedTime(startTime)

    measurer.asMilliseconds().shouldBeBetween(100.milliseconds.inWholeMilliseconds, 200.milliseconds.inWholeMilliseconds)

    delay(200.milliseconds)
    measurer.addElapsedTime(startTime)

    measurer.duration.get().shouldBeBetween(300.milliseconds.inWholeNanoseconds, 500.milliseconds.inWholeNanoseconds)
    measurer.asMilliseconds().shouldBeBetween(300.milliseconds.inWholeMilliseconds, 500.milliseconds.inWholeMilliseconds)
  }

  @Test
  fun `addMeasuredTime should measure time and return result`() = runBlocking {
    val measurer = NanosecondsMeasurer()

    val result = measurer.addMeasuredTime {
      delay(100.milliseconds)
      "Calculation Result"
    }

    measurer.duration.get().shouldBeBetween(100.milliseconds.inWholeNanoseconds, 150.milliseconds.inWholeNanoseconds)
    result.shouldBe("Calculation Result")
  }

  @Test
  fun `multiple invocation of addMeasuredTime should be summed up`() = runBlocking {
    val measurer = NanosecondsMeasurer()

    measurer.addMeasuredTime {
      delay(100.milliseconds)
      "Result"
    }

    val result = measurer.addMeasuredTime {
      delay(200.milliseconds)
      "Calculation Result"
    }

    measurer.duration.get().shouldBeBetween(300.milliseconds.inWholeNanoseconds, 500.milliseconds.inWholeNanoseconds)
    measurer.asMilliseconds().shouldBeBetween(300.milliseconds.inWholeMilliseconds, 500.milliseconds.inWholeMilliseconds)
    result.shouldBe("Calculation Result")
  }

  @Test
  fun `addMeasuredTime should measure time of empty block`(): Unit = runBlocking {
    val measurer = NanosecondsMeasurer()

    measurer.addMeasuredTime { }
    measurer.duration.get().shouldBeBetween(0, 10.milliseconds.inWholeNanoseconds)
  }
}