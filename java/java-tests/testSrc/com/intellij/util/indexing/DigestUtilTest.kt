// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.io.DigestUtil
import org.apache.commons.lang.RandomStringUtils
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DigestUtilTest {

  private val digest = DigestUtil.sha1()

  @Test
  fun `digest evaluation performance test`() {
    val stringsNumber = 1000
    val stringLength = 10000
    val randomBytes = (0 until stringsNumber)
      .map { RandomStringUtils.random(stringLength) }
      .map { it.toByteArray() }

    val threadPool = Executors.newFixedThreadPool(8)
    val callableList = randomBytes.map { Callable { calculateContentHash(it) } }

    PlatformTestUtil.startPerformanceTest("DigestUtil works quickly enough", 250) {
      threadPool.invokeAll(callableList)
    }.usesAllCPUCores().attempts(5).assertTiming()

    threadPool.shutdown()
    threadPool.awaitTermination(60, TimeUnit.SECONDS)
  }

  private fun calculateContentHash(bytes: ByteArray) {
    DigestUtil.calculateContentHash(digest, bytes)
  }

}