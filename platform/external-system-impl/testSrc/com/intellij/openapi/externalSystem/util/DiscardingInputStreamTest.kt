// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import kotlinx.coroutines.*
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.util.concurrent.CountDownLatch


class DiscardingInputStreamTest : DiscardingInputStreamTestCase() {

  @Test
  fun `test producer consumer`() {
    testIo(iterations = 10000) {
      val pipe = Pipe.open()
      val input = DiscardingInputStream(BufferedInputStream(Channels.newInputStream(pipe.source())))
      val output = BufferedOutputStream(Channels.newOutputStream(pipe.sink()))

      val startBarrier = CountDownLatch(1)
      val producedDataD = async {
        output.use {
          startBarrier.await()
          produceData(output, bufferSize = 1024)
        }
      }
      val consumedDataD = async {
        input.use {
          startBarrier.await()
          consumeData(input, bufferSize = 1024)
        }
      }

      startBarrier.countDown()

      val producedData = producedDataD.await()
      val consumedData = consumedDataD.await()

      output.close()
      input.close()

      Assertions.assertEquals(producedData, consumedData)
    }
  }

  @Test
  fun `test producer consumer without close`() {
    testIo(iterations = 10000) {
      val pipe = Pipe.open()
      val input = DiscardingInputStream(BufferedInputStream(Channels.newInputStream(pipe.source())))
      val output = BufferedOutputStream(Channels.newOutputStream(pipe.sink()))

      val startBarrier = CountDownLatch(1)
      val producedDataD = async {
        startBarrier.await()
        produceData(output, bufferSize = 1024)
      }
      val consumedDataD = async {
        startBarrier.await()
        consumeData(input, bufferSize = 1024)
      }

      startBarrier.countDown()

      val producedData = producedDataD.await()

      output.close()
      input.close()

      val consumedData = consumedDataD.await()

      if (!producedData.startsWith(consumedData)) {
        AssertionFailureBuilder.assertionFailure()
          .message("Consumed data has damage")
          .expected(producedData)
          .actual(consumedData)
          .buildAndThrow()
      }
    }
  }

  @Test
  fun `test producer without consumer`() {
    testIo(iterations = 10000) {
      val pipe = Pipe.open()
      val input = DiscardingInputStream(BufferedInputStream(Channels.newInputStream(pipe.source())))
      val output = BufferedOutputStream(Channels.newOutputStream(pipe.sink()))

      val producedDataD = async {
        produceData(output, bufferSize = 1024)
      }

      val producedData = producedDataD.await()

      output.close()
      input.close()

      Assertions.assertTrue(producedData.isNotEmpty()) { "Data isn't produced" }
    }
  }
}