// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

import kotlin.jvm.JvmInline

@JvmInline
value class Codepoint(val codepoint: Int) {
  val charCount: Int get() = Character.charCount(codepoint)
}

fun CharSequence.codepoints(offset: Int, direction: Direction): Iterator<Codepoint> =
  when (direction) {
    Direction.FORWARD -> iterator {
      var i = offset
      val len = length
      while (i < len) {
        val c1 = get(i++)
        if (Character.isHighSurrogate(c1)) {
          if (i < len) {
            val c2 = get(i++)
            if (Character.isLowSurrogate(c2)) {
              yield(Codepoint(Character.toCodePoint(c1, c2)))
            }
          }
        }
        else {
          yield(Codepoint(c1.code))
        }
      }
    }
    Direction.BACKWARD -> iterator {
      var i = offset - 1
      while (i >= 0) {
        val c2 = get(i--)
        if (Character.isLowSurrogate(c2)) {
          if (i >= 0) {
            val c1 = get(i--)
            if (Character.isHighSurrogate(c1)) {
              yield(Codepoint(Character.toCodePoint(c1, c2)))
            }
          }
        }
        else {
          yield(Codepoint(c2.code))
        }
      }
    }
  }
