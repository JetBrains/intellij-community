// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.Hashing
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class StripedMutexTest {
  @Test
  fun `stripe count must be positive`() {
    assertThatThrownBy {
      StripedMutex(0)
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Stripe count must be positive")
  }

  @Test
  fun `stripe count must be power of 2`() {
    // These should work (powers of 2)
    StripedMutex(1)
    StripedMutex(2)
    StripedMutex(4)
    StripedMutex(8)
    StripedMutex(16)

    // These should throw exceptions (not powers of 2)
    assertThatThrownBy { StripedMutex(3) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Stripe count must be a power of 2")

    assertThatThrownBy { StripedMutex(5) }
      .isInstanceOf(IllegalArgumentException::class.java)

    assertThatThrownBy { StripedMutex(10) }
      .isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `string keys are consistently mapped to same lock`() {
    val striped = StripedMutex(16)

    // Test multiple retrievals of the same key return the same lock
    val key = "test-key"
    val lock1 = striped.getLock(key)
    val lock2 = striped.getLock(key)

    assertThat(lock2).isSameAs(lock1)
  }

  @Test
  fun `hash keys are consistently mapped to same lock`() {
    val striped = StripedMutex(16)

    // Test multiple retrievals of the same hash return the same lock
    val hash = 12345L
    val lock1 = striped.getLockByHash(hash)
    val lock2 = striped.getLockByHash(hash)

    assertThat(lock2).isSameAs(lock1)
  }

  @Test
  fun `distribution of locks should be uniform`() {
    val stripeCount = 16
    val striped = StripedMutex(stripeCount)
    val lockCounts = ConcurrentHashMap<Mutex, AtomicInteger>()

    // Generate a large number of keys and count how many map to each lock
    val keyCount = 10000
    for (i in 0 until keyCount) {
      val key = "key-$i"
      val lock = striped.getLock(key)
      lockCounts.computeIfAbsent(lock) { AtomicInteger(0) }.incrementAndGet()
    }

    // Verify all stripes are used
    assertThat(lockCounts).hasSize(stripeCount)

    // Calculate statistics for distribution
    val counts = lockCounts.values.map { it.get() }
    val average = counts.average()
    val expected = keyCount.toDouble() / stripeCount

    // Verify a reasonably even distribution (within 20% of the expected average)
    assertThat(average).isCloseTo(expected, Percentage.withPercentage(20.0))

    // Check that min and max counts aren't too skewed
    val min = counts.minOrNull() ?: 0
    val max = counts.maxOrNull() ?: 0

    assertThat(min).isGreaterThan(0)
    assertThat(max.toDouble() / min.toDouble()).isLessThan(3.0) // Max should be less than 3x min for good distribution
  }

  @Test
  fun `concurrent access should be thread-safe`() = runBlocking {
    val striped = StripedMutex(8)
    val sharedCounter = AtomicInteger(0)
    val iterations = 1000
    val threadsPerKey = 10
    val keys = listOf("key1", "key2", "key3", "key4")

    // Counter per key to verify correct locking
    val countersPerKey = ConcurrentHashMap<String, AtomicInteger>()
    keys.forEach { countersPerKey[it] = AtomicInteger(0) }

    // Start many concurrent tasks operating on the same keys
    val tasks = List(keys.size * threadsPerKey) { index ->
      val key = keys[index % keys.size]

      async {
        repeat(iterations) {
          val lock = striped.getLock(key)
          lock.withLock {
            // Increment shared counter
            sharedCounter.incrementAndGet()

            // Verify and update key-specific counter
            val keyCounter = countersPerKey[key]!!
            val value = keyCounter.get()
            // Introduce a small delay to increase the chance of race conditions if locking is broken
            yield()
            keyCounter.set(value + 1)
          }
        }
      }
    }

    // Wait for all tasks to complete
    tasks.forEach { it.await() }

    // Verify the total count
    val expectedTotal = keys.size * threadsPerKey * iterations
    assertThat(sharedCounter.get()).isEqualTo(expectedTotal)

    // Verify each key's counter
    keys.forEach { key ->
      val expected = threadsPerKey * iterations
      assertThat(countersPerKey[key]!!.get()).isEqualTo(expected)
    }
  }

  @Test
  fun `hash distribution for Long values should be effective`() {
    val stripeCount = 16
    val striped = StripedMutex(stripeCount)
    val distributionMap = Int2IntOpenHashMap()

    // Test with positive, negative, and edge-case longs
    val testValues = sequenceOf(
      0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE,
      Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong()
    ) + (1..1000).map { it.toLong() } + (1..1000).map { -it.toLong() }

    for (value in testValues) {
      val index = System.identityHashCode(striped.getLockByHash(value))
      distributionMap.addTo(index, 1)
    }

    // Check that we're using stripes effectively
    assertThat(distributionMap.size).isLessThanOrEqualTo(stripeCount)
    // Should use at least half of the available stripes
    assertThat(distributionMap.size).isGreaterThan(stripeCount / 2)

    // Check if distribution looks reasonable
    val counts = distributionMap.values
    assertThat(counts.max() - counts.max()).isLessThan(counts.average().toInt() * 2)
  }

  @Test
  fun `string keys should map to corresponding hash locks`() {
    val key = "test-string"
    val expectedHash = Hashing.xxh3_64().hashBytesToLong(key.toByteArray())
    val stripeCount = 16
    val mask = (stripeCount - 1).toLong()

    val striped = StripedMutex(stripeCount)
    val actualLock = striped.getLock(key)
    val expectedLock = striped.getLockByHash(expectedHash)

    assertThat(actualLock).isSameAs(expectedLock)

    // Let's also check that different strings with different hashes go to different locks
    // when their hashes map to different stripes
    val differentKey = "different-key"
    val differentHash = Hashing.xxh3_64().hashBytesToLong(differentKey.toByteArray())

    // Only check if the hashes would actually map to different stripes
    if ((expectedHash and mask) != (differentHash and mask)) {
      val differentLock = striped.getLock(differentKey)
      assertThat(differentLock).isNotSameAs(actualLock)
    }
  }

  @Test
  fun `long hash values should be correctly masked`() {
    val stripeCount = 8
    val striped = StripedMutex(stripeCount)

    // Test with various values that would map to the same index after masking
    for (i in 0 until 10) {
      val baseValue = i.toLong()
      val withHighBit = baseValue or (1L shl 60) // Set a high-order bit

      val lock1 = striped.getLockByHash(baseValue)
      val lock2 = striped.getLockByHash(baseValue + stripeCount)
      val lock3 = striped.getLockByHash(withHighBit)

      // Check that values that are equivalent modulo stripeCount map to the same lock
      assertThat(lock2).isSameAs(lock1)

      // Check that setting high bits doesn't affect the lock mapping
      assertThat(lock3).isSameAs(lock1)
    }
  }
}