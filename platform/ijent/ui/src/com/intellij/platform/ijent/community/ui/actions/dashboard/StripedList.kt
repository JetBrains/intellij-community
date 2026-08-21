// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

internal class StripedList(capacity: Int) {
  private val powerOfTwoCapacity = cclp2(capacity)
  private val mask = powerOfTwoCapacity - 1
  private val elements = LongArray(powerOfTwoCapacity) { EMPTY }

  fun add(value: Long): Boolean {
    require(value != EMPTY) { "$EMPTY is reserved" }

    var index = mix(value).toInt() and mask

    repeat(elements.size) {
      if (element.compareAndSet(elements, index, EMPTY, value)) {
        return true
      }
      index = (index + 1) and mask
    }
    return false
  }

  fun remove(value: Long): Boolean {
    var index = mix(value).toInt() and mask

    repeat(elements.size) {
      if ((element.getAcquire(elements, index) as Long) == value && element.compareAndSet(elements, index, value, EMPTY)) {
        return true
      }
      index = (index + 1) and mask
    }
    return false
  }

  fun snapshot(): LongArray {
    val result = LongArray(elements.size)
    var size = 0

    for (index in elements.indices) {
      val value = element.getAcquire(elements, index) as Long
      if (value != EMPTY) result[size++] = value
    }
    return result.copyOf(size)
  }

  private companion object {
    const val EMPTY = Long.MIN_VALUE

    val element: VarHandle =
      MethodHandles.arrayElementVarHandle(LongArray::class.java)

    fun cclp2(value: Int): Int {
      require(value in 1..(1 shl 10))
      return if (value == 1) 1
      else 1 shl (32 - Integer.numberOfLeadingZeros(value - 1))
    }

    fun mix(value: Long): Long {
      var x = value
      x = (x xor (x ushr 33)) * -49064778989728563L
      x = (x xor (x ushr 33)) * -4265267296055464877L
      return x xor (x ushr 33)
    }
  }
}
