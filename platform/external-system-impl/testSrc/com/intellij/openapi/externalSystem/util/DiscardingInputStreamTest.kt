// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit


class DiscardingInputStreamTest {

  @Test
  fun `test producer consumer`() {
    testIo { input, output ->
      val producedDataD = async {
        output.use {
          produceData(output, 10)
        }
      }
      val consumedDataD = async {
        input.use {
          consumeData(input, 1024)
        }
      }

      producedDataD.start()
      consumedDataD.start()

      val producedData = producedDataD.await()
      val consumedData = consumedDataD.await()

      input.close()

      Assertions.assertEquals(producedData, consumedData)
    }
  }

  @Test
  fun `test producer consumer without close`() {
    testIo { input, output ->
      val producedDataD = async { produceData(output, 10) }
      val consumedDataD = async { consumeData(input, 1024) }

      producedDataD.start()
      consumedDataD.start()

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
    testIo { input, output ->
      val producedDataD = async { produceData(output, 10) }

      val producedData = producedDataD.await()

      output.close()
      input.close()

      Assertions.assertTrue(producedData.isNotEmpty()) { "Data isn't produced" }
    }
  }

  private fun testIo(action: suspend CoroutineScope.(DiscardingInputStream, OutputStream) -> Unit) {
    val executor = ConcurrencyUtil.newSingleThreadExecutor("Test IO")
    try {
      repeat(10000) {
        executor.submit {
          runBlocking {
            withContext(Dispatchers.IO) {
              val pipe = Pipe.open()
              val input = DiscardingInputStream(BufferedInputStream(Channels.newInputStream(pipe.source())))
              val output = BufferedOutputStream(Channels.newOutputStream(pipe.sink()))
              action(input, output)
            }
          }
        }.get(1, TimeUnit.SECONDS)
      }
    }
    finally {
      executor.shutdown()
    }
  }

  private fun produceData(output: OutputStream, dataMultiplier: Int): String {
    val buffer = StringBuilder()
    repeat(dataMultiplier) {
      val data = "Data$it,"
      buffer.append(data)
      output.write(data.toByteArray(StandardCharsets.UTF_8))
      output.flush()
    }
    return buffer.toString()
  }

  private fun consumeData(input: InputStream, bufferSize: Int): String {
    var length = 0
    val buffer = ByteArray(bufferSize)
    while (true) {
      val numRead = input.read(buffer, length, buffer.size - length)
      if (numRead < 0) break
      length += numRead
    }
    return String(buffer, 0, length, StandardCharsets.UTF_8)
  }
}