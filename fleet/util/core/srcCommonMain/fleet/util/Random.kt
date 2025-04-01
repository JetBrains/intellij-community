// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.multiplatform.shims.synchronized
import kotlinx.datetime.Clock
import kotlin.random.Random

object Random {
  private val cacheBits = ByteArray(8 * 1024)
  private var served: Int = cacheBits.size

  private val rnd = Random(Clock.System.now().toEpochMilliseconds())

  fun nextBytes(len: Int): ByteArray {
    return ByteArray(len).also { answer ->
      if (len >= cacheBits.size) {
        rnd.nextBytes(answer)
      }
      else {
        synchronized(cacheBits) {
          if (served + len > cacheBits.size) {
            rnd.nextBytes(cacheBits)
            served = 0
          }
          cacheBits.copyInto(answer, 0, served, served + len)
          served += len
        }
      }
    }
  }

  fun nextLong(): Long {
    val l = nextBytes(Long.SIZE_BYTES).toLong()

    // Disallow zeros & negatives
    return when {
      l == 0L -> 1
      l < 0L -> -l
      else -> l
    }
  }

  internal fun nextUidString(len: Int): String {
    val string = buildString {
      repeat(len / 8 + 1) {
        // Would give 8 chars per chunk (radix 32 -> 5 bits per char, 5 * 8 bits generated)
        append(nextBytes(5).toLong().toString(32).padStart(8, '0'))
      }
    }

    return string.take(len)
  }

  fun nextInt(bound: Int): Int {
    val i = nextBytes(Int.SIZE_BYTES).toLong().toInt() % bound

    // Disallow zeros & negatives
    return when {
      i == 0 -> 1
      i < 0 -> -i
      else -> i
    }
  }
}

private fun ByteArray.toLong(): Long {
  var number = 0L
  for ((index, i) in (size - 1 downTo 0).withIndex()) { // big-endian
    val bitIndex = i * 8
    number = get(index).toLong() and 0xff shl bitIndex or number
  }
  return number
}
