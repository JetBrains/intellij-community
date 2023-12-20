// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.DurableDataEnumerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(JUnit4::class)
class PersistentTimestampsEnumeratorTest {
  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @Rule
  @JvmField
  val temp = TempDirectory()

  lateinit var enumerator: DurableDataEnumerator<TimestampsImmutable>

  @Before
  fun setup() {
    val dir = temp.newDirectory("persistentTimestampsEnumerator").toPath()
    enumerator = DurableTimestampsEnumerator(dir.resolve("enumerator"))
  }

  @After
  fun tearDown() {
    enumerator.close()
  }

  @Test
  fun testStoreRetrieve() {
    val idx1 = ID.create<Any, Any>("testStoreRetrieve.1")
    val idx2 = ID.create<Any, Any>("testStoreRetrieve.2")
    val timeBase = DataInputOutputUtil.timeBase + ID.MAX_NUMBER_OF_INDICES

    val ts = Timestamps().apply {
      set(idx1, timeBase + 1)
      set(idx2, timeBase + 2)
    }.toImmutable()

    val stored = enumerator.enumerate(ts)
    val restored = enumerator.valueOf(stored)
    assertEquals(ts, restored)
  }

  @Test
  fun testAccessingNotPersisted() {
    val eid = enumerator.tryEnumerate(TimestampsImmutable.EMPTY)
    assertEquals("Enumerator should be empty", 0, eid)
    val nonExisting = enumerator.valueOf(42)
    assertNull(nonExisting)
  }

  @Test
  fun testConcurrentReadWrite() {
    val idx = ID.create<Any, Any>("testConcurrentReadWrite.1")
    val stored = ConcurrentHashMap<TimestampsImmutable, Int>()
    val timestamp = AtomicLong(DataInputOutputUtil.timeBase + ID.MAX_NUMBER_OF_INDICES)
    val writeCount = AtomicInteger(0)
    val readCount = AtomicInteger(0)

    runBlocking(Dispatchers.IO) {
      val running = AtomicBoolean(true)

      launch {
        delay(1_000)
        running.set(false)
      }

      repeat(8) {
        launch {
          while (running.get()) {
            val ts = Timestamps().apply {
              set(idx, timestamp.incrementAndGet())
            }.toImmutable()
            val enumerated = enumerator.enumerate(ts)
            stored.put(ts, enumerated)
            writeCount.incrementAndGet()
          }
        }
      }

      repeat(4) {
        launch {
          while (running.get()) {
            stored.forEach { (k, v) ->
              val restored = enumerator.valueOf(v)
              assertEquals(k, restored)
              readCount.incrementAndGet()
            }
          }
        }
      }
    }

    println("write: ${writeCount.get()}, read: ${readCount.get()}")
  }
}