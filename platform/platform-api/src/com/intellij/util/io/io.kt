/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io

import com.intellij.util.text.CharArrayCharSequence
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*

fun InputStreamReader.readCharSequence(length: Int): CharSequence {
  use {
    val chars = CharArray(length)
    var count = 0
    while (count < chars.size) {
      val n = read(chars, count, chars.size - count)
      if (n <= 0) {
        break
      }
      count += n
    }
    return CharSequenceBackedByChars(chars, 0, count)
  }
}

// we must return string on subSequence() - JsonReaderEx will call toString in any case
class CharSequenceBackedByChars : CharArrayCharSequence {
  val byteBuffer: ByteBuffer
    get() = Charsets.UTF_8.encode(CharBuffer.wrap(myChars, myStart, length))

  constructor(charBuffer: CharBuffer) : super(charBuffer.array(), charBuffer.arrayOffset(), charBuffer.position()) {
  }

  constructor(chars: CharArray, start: Int, end: Int) : super(chars, start, end) {
  }

  constructor(chars: CharArray) : super(*chars) {
  }

  override fun subSequence(start: Int, end: Int): CharSequence {
    return if (start == 0 && end == length) this else String(myChars, myStart + start, end - start)
  }
}

fun ByteBuffer.toByteArray(): ByteArray {
  if (hasArray()) {
    val offset = arrayOffset()
    if (offset == 0 && array().size == limit()) {
      return array()
    }
    return Arrays.copyOfRange(array(), offset, offset + limit())
  }

  val bytes = ByteArray(limit())
  get(bytes)
  return bytes
}

fun String.encodeUrlQueryParameter() = URLEncoder.encode(this, Charsets.UTF_8.name())!!

fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)