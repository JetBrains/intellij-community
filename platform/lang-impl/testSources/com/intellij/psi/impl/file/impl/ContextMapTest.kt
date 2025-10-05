// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.util.ref.GCWatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull

class ContextMapTest {
  @Test
  fun testDefaultValueExists1() {
    val context1 = MockContext("context1")
    val value1 = Value("value1")

    val map = emptyContextMap<Value>().add(context1, value1)

    assertEquals(1, map.size())
    assertNotNull(map.defaultValue())
  }

  @Test
  fun testDefaultValueExists2() {
    val context1 = MockContext("context1")
    val value1 = Value("value1")
    val context2 = MockContext("context2")
    val value2 = Value("value2")

    val map = emptyContextMap<Value>().add(context1, value1).add(context2, value2)

    assertEquals(2, map.size())
    assertNotNull(map.defaultValue())
  }

  @Test
  fun testDefaultValueExists3() {
    val context1 = MockContext("context1")
    val value1 = Value("value1")
    val context2 = MockContext("context2")
    val value2 = Value("value2")
    val context3 = MockContext("context3")
    val value3 = Value("value3")

    val map = emptyContextMap<Value>().add(context1, value1).add(context2, value2).add(context3, value3)

    assertEquals(3, map.size())
    assertNotNull(map.defaultValue())
  }

  @Test
  fun testDefaultValueCollected1() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    val context3 = MockContext("context3")
    // value1 is not referenced from anywhere else, so it should be collected
    val value2 = Value("value2")
    val value3 = Value("value3")

    fun initMap3(): Pair<ContextMap<Value>, GCWatcher> {
      val value1 = Value("value1")

      val map3 = emptyContextMap<Value>()
        .add(context1, value1)
        .add(context2, value2)
        .add(context3, value3)

      return map3 to GCWatcher.tracking(value1)
    }

    val (map3, value1Tracker) = initMap3()

    value1Tracker.ensureCollected()
    val mapWithEvictedValue1 = map3.processQueue()

    assertEquals(2, mapWithEvictedValue1.size())
    assertNotNull(mapWithEvictedValue1.defaultValue())
  }

  @Test
  fun testDefaultValueCollected2() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    val context3 = MockContext("context3")
    // value1 is not referenced from anywhere else, so it should be collected
    // value2 is not referenced from anywhere else, so it should be collected
    val value3 = Value("value3")

    fun initMap3(): Pair<ContextMap<Value>, List<GCWatcher>> {
      val value1 = Value("value1")
      val value2 = Value("value2")

      val map3 = emptyContextMap<Value>()
        .add(context1, value1)
        .add(context2, value2)
        .add(context3, value3)

      return map3 to listOf(GCWatcher.tracking(value1), GCWatcher.tracking(value2))
    }

    val (map3, trackers) = initMap3()

    for (watcher in trackers) {
      watcher.ensureCollected()
    }
    val mapWithEvictedValue1And2 = map3.processQueue()

    assertEquals(1, mapWithEvictedValue1And2.size())
    assertEquals(value3, mapWithEvictedValue1And2.defaultValue())
  }

  @Test
  fun testDefaultValueCollected3() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    val context3 = MockContext("context3")
    // value1 is not referenced from anywhere else, so it should be collected
    // value2 is not referenced from anywhere else, so it should be collected
    // value3 is not referenced from anywhere else, so it should be collected

    fun initMap3(): Pair<ContextMap<Value>, List<GCWatcher>> {
      val value1 = Value("value1")
      val value2 = Value("value2")
      val value3 = Value("value3")

      val map3 = emptyContextMap<Value>()
        .add(context1, value1)
        .add(context2, value2)
        .add(context3, value3)

      return map3 to listOf(GCWatcher.tracking(value1), GCWatcher.tracking(value2), GCWatcher.tracking(value3))
    }

    val (map3, trackers) = initMap3()

    for (watcher in trackers) {
      watcher.ensureCollected()
    }
    val emptyMap = map3.processQueue()

    assertEquals(0, emptyMap.size())
  }

  @Test
  fun testDefaultValueCollectedAndThenRequested() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    // value1 is not referenced from anywhere else, so it should be collected
    val value2 = Value("value2")

    fun initMap2(): Pair<ContextMap<Value>, GCWatcher> {
      val value1 = Value("value1")

      val map2 = emptyContextMap<Value>()
        .add(context1, value1)
        .add(context2, value2)

      return map2 to GCWatcher.tracking(value1)
    }

    val (map2, value1Tracker) = initMap2()

    value1Tracker.ensureCollected()

    // value1 is collected, but defaultValue is stable, thus it's null
    assertNull(map2.defaultValue())

    // after processing queue, defaultValue should be reassigned
    assertNotNull(map2.processQueue().defaultValue())
  }

  @Test
  fun testValueCollectedAndThenNewAdded() {
    val context1 = MockContext("context1")
    val context2 = MockContext("context2")
    val context3 = MockContext("context3")
    // value1 is not referenced from anywhere else, so it should be collected
    val value2 = Value("value2")
    val value3 = Value("value3")

    fun initMap2(): Pair<ContextMap<Value>, GCWatcher> {
      val value1 = Value("value1")

      val map2 = emptyContextMap<Value>()
        .add(context1, value1)
        .add(context2, value2)

      return map2 to GCWatcher.tracking(value1)
    }

    val (map2, value1Tracker) = initMap2()

    value1Tracker.ensureCollected()

    val mapWith1 = map2.add(context3, value3)

    assertNotNull(mapWith1.defaultValue())
    assertEquals(2, mapWith1.size())
  }
}

private data class MockContext(val name: String = "") : CodeInsightContext

private data class Value(val name: String = "")