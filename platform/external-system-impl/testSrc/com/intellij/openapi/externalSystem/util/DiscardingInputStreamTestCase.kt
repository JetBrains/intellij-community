// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit


abstract class DiscardingInputStreamTestCase {

  fun testIo(iterations: Int, action: suspend CoroutineScope.() -> Unit) {
    val executor = ConcurrencyUtil.newSingleThreadExecutor("Test IO")
    try {
      repeat(iterations) {
        executor.submit {
          runBlocking {
            withContext(Dispatchers.IO) {
              action()
            }
          }
        }.get(1, TimeUnit.SECONDS)
      }
    }
    finally {
      executor.shutdown()
    }
  }

  fun produceData(output: OutputStream, bufferSize: Int): String {
    val buffer = StringBuilder()
    val data = "Data,"
    while (buffer.length + data.length < bufferSize) {
      buffer.append(data)
      output.write(data.toByteArray(StandardCharsets.UTF_8))
      output.flush()
    }
    return buffer.toString()
  }

  fun consumeData(input: InputStream, bufferSize: Int): String {
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