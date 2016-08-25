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
package com.intellij.credentialStore

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.nullize
import com.intellij.util.text.CharArrayCharSequence
import org.jetbrains.io.toByteArray
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

data class CredentialAttributes(val serviceName: String, val userName: String? = null) {
}

// user cannot be empty, but password can be
class Credentials(user: String?, val password: OneTimeString?) {
  val userName = user.nullize()

  fun getPasswordAsString() = password?.toString()

  override fun equals(other: Any?): Boolean {
    if (other !is Credentials) return false
    return userName == other.userName && password == other.password
  }

  override fun hashCode() = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)
}

fun CredentialAttributes(requestor: Class<*>, userName: String) = CredentialAttributes(requestor.name, userName)

// input will be cleared
@JvmOverloads
fun SecureString(value: ByteArray, offset: Int = 0, length: Int = value.size - offset): OneTimeString {
  if (length == 0) {
    return OneTimeString(ArrayUtil.EMPTY_CHAR_ARRAY)
  }

  // jdk decodes to heap array, but since this code is very critical, we cannot rely on it, so, we don't use Charsets.UTF_8.decode()
  val charsetDecoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
  val charArray = CharArray((value.size * charsetDecoder.maxCharsPerByte().toDouble()).toInt())
  charsetDecoder.reset()
  val charBuffer = CharBuffer.wrap(charArray)
  var cr = charsetDecoder.decode(ByteBuffer.wrap(value, offset, length), charBuffer, true)
  if (!cr.isUnderflow) {
    cr.throwException()
  }
  cr = charsetDecoder.flush(charBuffer)
  if (!cr.isUnderflow) {
    cr.throwException()
  }

  value.fill(0, offset, offset + length)
  return OneTimeString(charArray, 0, charBuffer.position())
}

// todo - eliminate toString
class OneTimeString(value: CharArray, offset: Int = 0, length: Int = value.size) : CharArrayCharSequence(value, offset, offset + length) {
  private val consumed = AtomicBoolean()

  constructor(value: String): this(value.toCharArray()) {
  }

  @JvmOverloads
  fun toString(clear: Boolean = true): String {
    if (clear && !consumed.compareAndSet(false, true)) {
      throw Error("Already consumed")
    }
    // todo clear
    return super.toString()
  }

  // string will be cleared and not valid after
  fun toByteArray(): ByteArray {
    if (!consumed.compareAndSet(false, true)) {
      throw Error("Already consumed")
    }

    val result = Charsets.UTF_8.encode(CharBuffer.wrap(myChars, myStart, length))
    myChars.fill('\u0000', myStart, myEnd)
    return result.toByteArray()
  }

  fun toCharArray(): CharArray {
    if (!consumed.compareAndSet(false, true)) {
      throw Error("Already consumed")
    }

    // todo clear
    return chars
  }

  override fun equals(other: Any?): Boolean {
    if (other is CharSequence) {
      return StringUtil.equals(this, other)
    }
    return super.equals(other)
  }
}