// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.util

import org.junit.Assert.*
import org.junit.Test

class IntObjectMapTest {
  @Test
  fun `set and get`() {
    val map = IntObjectMap<Int>(4)
    map.set(0, 10)
    map.set(1, 11)
    assertEquals(10, map.get(0))
    assertEquals(11, map.get(1))
    assertEquals(2, map.size())
  }

  @Test
  fun `set and get with expand`() {
    val map = IntObjectMap<Int>(4)
    map.set(5, 10)
    assertEquals(10, map.get(5))
    assertEquals(1, map.size())
  }

  @Test
  fun `expand and enough space`() {
    val map = IntObjectMap<Int>(4)
    map.set(1, 10)
    map.shiftKeys(0, 2)
    assertNull(map.get(1))
    assertEquals(10, map.get(3))
    assertEquals(1, map.size())
  }

  @Test
  fun `expand and not enough space`() {
    val map = IntObjectMap<Int>(4)
    map.set(3, 10)
    map.shiftKeys(3, 2)
    assertNull(map.get(3))
    assertEquals(10, map.get(5))
    assertEquals(1, map.size())
  }

  @Test
  fun `expand on first insert`() {
    val map = IntObjectMap<Int>(4)
    map.set(4, 10)
    assertEquals(10, map.get(4))
    assertEquals(1, map.size())
  }

  @Test
  fun `composite`() {
    val map = IntObjectMap<Int>(4)
    map.set(1, 10)
    map.set(2, 20)
    map.set(5, 50)
    map.shiftKeys(2, 10)

    assertEquals(10, map.get(1))
    assertNull(map.get(2))
    assertNull(map.get(5))
    assertEquals(20, map.get(12))
    assertEquals(50, map.get(15))

    map.remove(15)
    assertNull(map.get(15))

    map.shiftKeys(0, 10)
    assertNull(map.get(1))
    assertEquals(10, map.get(11))
    assertNull(map.get(15))
    assertEquals(20, map.get(22))
    assertEquals(2, map.size())
  }

  @Test
  fun `shift backwards starting from null`() {
    val map = IntObjectMap<Int>(4)
    map.set(1, 10)
    map.set(3, 30)
    map.set(4, 40)

    map.shiftKeys(2, -1)
    assertEquals(10, map.get(1))
    assertEquals(30, map.get(2))
    assertEquals(40, map.get(3))
    assertNull(map.get(4))
    assertEquals(3, map.size())
  }

  @Test
  fun `clear`() {
    val map = IntObjectMap<Int>()
    map.set(1, 10)
    map.set(2, 20)
    assertEquals(2, map.size())

    map.clear()
    assertEquals(0, map.size())
    assertNull(map.get(1))
    assertNull(map.get(2))
  }

  @Test
  fun `duplicate operations and size`() {
    val map = IntObjectMap<Int>()

    map.set(1, 10)
    assertEquals(1, map.size())
    assertEquals(10, map.get(1))

    map.set(1, 100)
    assertEquals(1, map.size())
    assertEquals(100, map.get(1))

    map.remove(1)
    assertEquals(0, map.size())
    assertNull(map.get(1))

    map.remove(1)
    assertEquals(0, map.size())
    assertNull(map.get(1))
  }
}