// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.lang.RuntimeException

class SharedIndexesLoaderTest : BasePlatformTestCase() {
  @Test
  fun test_read_random_hash_ok() {
    val indexes = networkRetry {
      SharedIndexesLoader.getInstance()
        .downloadIndexesList("jdk", "9f29c2ba6436e7dc64b011d71f14918f0b7e3e7f", null)
    }
    Assert.assertTrue("$indexes", indexes.isNotEmpty())
  }

  @Test
  fun test_read_random_hash_miss() {
    val indexes = networkRetry {
      SharedIndexesLoader.getInstance()
        .downloadIndexesList("jdk", "9f29c2ba6436__missing__e71f14918f0b7e3e7f", null)
    }

    Assert.assertTrue("$indexes", indexes.isEmpty())
  }

  private inline fun <Y> networkRetry(action: () -> Y): Y {
    lateinit var lastError: Throwable
    run {
      repeat(5) {
        val result = runCatching {
          return action()
        }
        lastError = result.exceptionOrNull()!!
        if (lastError is IOException) {
          Thread.sleep(5000)
        }
        else throw lastError
      }
    }
    throw RuntimeException("Failed to wait for Network to fix. ${lastError.message}", lastError)
  }
}

