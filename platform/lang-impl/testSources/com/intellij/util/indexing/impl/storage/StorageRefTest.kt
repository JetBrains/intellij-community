// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(10)
internal class StorageRefTest {
  @Test
  fun `reopen rejects an open storage when strict checking is enabled`() {
    val storageRef = StorageRef<TestStorage, IOException>("test", ::TestStorage, TestStorage::closed, true)
    val storage = storageRef.reopen()

    assertThatThrownBy { storageRef.reopen() }.isInstanceOf(IllegalStateException::class.java)

    storage.close()
  }

  @Test
  fun `reopen waits until the cleanup task completes`() {
    val cleanupStarted = CountDownLatch(1)
    val finishCleanup = CountDownLatch(1)
    val reopenStarted = CountDownLatch(1)
    val storageOpened = CountDownLatch(1)
    val storageRef = StorageRef<TestStorage, IOException>(
      "test",
      {
        storageOpened.countDown()
        TestStorage()
      },
      TestStorage::closed,
      true,
    )
    val executor = Executors.newFixedThreadPool(2)

    try {
      val cleanup = executor.submit {
        storageRef.ensureClosedAndRun<InterruptedException> {
          cleanupStarted.countDown()
          finishCleanup.await()
        }
      }
      assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue()

      val reopen = executor.submit<TestStorage> {
        reopenStarted.countDown()
        storageRef.reopen()
      }
      assertThat(reopenStarted.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(storageOpened.await(200, TimeUnit.MILLISECONDS)).isFalse()

      finishCleanup.countDown()
      cleanup.get(5, TimeUnit.SECONDS)
      reopen.get(5, TimeUnit.SECONDS).close()
      assertThat(storageOpened.count).isZero()
    }
    finally {
      finishCleanup.countDown()
      executor.shutdownNow()
    }
  }

  private class TestStorage : Closeable {
    var closed: Boolean = false
      private set

    override fun close() {
      closed = true
    }
  }
}
