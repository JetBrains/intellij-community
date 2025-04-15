// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlin.math.min
import kotlin.jvm.Transient

data class CharArrayCharSequence(val myChars: CharArray, val myStart: Int, val myEnd: Int) : CharSequence {
  init {
    if (myStart < 0 || myEnd > chars.size || myStart > myEnd) {
      throw IndexOutOfBoundsException("chars.length:" + chars.size + ", start:" + myStart + ", end:" + myEnd)
    }
  }

  constructor(vararg chars: Char) : this(chars, 0, chars.size)

  override val length: Int
    get() {
      return myEnd - myStart
    }

  override fun get(index: Int): Char {
    return myChars[index + myStart]
  }

  override fun subSequence(start: Int, end: Int): CharSequence {
    return if (start == 0 && end == length) this else CharArrayCharSequence(myChars, myStart + start, myStart + end)
  }

  override fun toString(): String {
    return myChars.concatToString(myStart, myEnd) //TODO StringFactory
  }

  val chars: CharArray
    get() {
      if (myStart == 0) return myChars
      val chars = CharArray(length)
      getChars(chars, 0)
      return chars
    }

  fun getChars(dst: CharArray, dstOffset: Int) {
    myChars.copyInto(dst, dstOffset, myStart, myEnd)
  }

  override fun equals(anObject: Any?): Boolean {
    if (anObject is CharArrayCharSequence) {
      return anObject.length == length && myChars.regionMatches(myStart, myEnd, anObject)
    }
    return false
  }

  /**
   * See [java.io.Reader.read];
   */
  fun readCharsTo(start: Int, cbuf: CharArray, off: Int, len: Int): Int {
    val readChars = min(len.toDouble(), (length - start).toDouble()).toInt()
    if (readChars <= 0) return -1

    myChars.copyInto(cbuf, off, myStart + start, myStart + start + readChars)
    return readChars
  }

  @Transient
  private var hash = 0

  override fun hashCode(): Int {
    var h = hash
    for (off in myStart until myEnd) {
      h = 31 * h + chars[off].code
    }
    return h
  }
}
