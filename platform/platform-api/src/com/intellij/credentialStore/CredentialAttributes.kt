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
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.toByteArray
import com.intellij.util.text.CharArrayCharSequence
import com.intellij.util.text.nullize
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicReference

const val SERVICE_NAME_PREFIX = "IntelliJ Platform"

fun generateServiceName(subsystem: String, key: String) = "$SERVICE_NAME_PREFIX $subsystem — $key"

/**
 * requestor is deprecated. Never use it in new code.
 */
data class CredentialAttributes @JvmOverloads constructor(val serviceName: String, val userName: String? = null, val requestor: Class<*>? = null, val isPasswordMemoryOnly: Boolean = false)

fun CredentialAttributes.toPasswordStoreable() = if (isPasswordMemoryOnly) CredentialAttributes(serviceName, userName, requestor) else this

// user cannot be empty, but password can be
class Credentials(user: String?, val password: OneTimeString? = null) {
  constructor(user: String?, password: String?) : this(user, password?.let(::OneTimeString))

  constructor(user: String?, password: CharArray?) : this(user, password?.let { OneTimeString(it) })

  constructor(user: String?, password: ByteArray?) : this(user, password?.let { OneTimeString(password) })

  val userName = user.nullize()

  fun getPasswordAsString() = password?.toString()

  override fun equals(other: Any?): Boolean {
    if (other !is Credentials) return false
    return userName == other.userName && password == other.password
  }

  override fun hashCode() = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)
}

/**
 * DEPRECATED. Never use it in a new code.
 */
fun CredentialAttributes(requestor: Class<*>, userName: String?) = CredentialAttributes(requestor.name, userName, requestor)

fun Credentials?.isFulfilled() = this != null && userName != null && !password.isNullOrEmpty()
fun Credentials?.hasOnlyUserName() = this != null && userName != null && password.isNullOrEmpty()

fun Credentials?.isEmpty() = this == null || (userName == null && password.isNullOrEmpty())

// input will be cleared
@JvmOverloads
fun OneTimeString(value: ByteArray, offset: Int = 0, length: Int = value.size - offset, clearable: Boolean = false): OneTimeString {
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
  return OneTimeString(charArray, 0, charBuffer.position(), clearable = clearable)
}

/**
 * clearable only if specified explicitly.
 *
 * Case
 * 1) you create OneTimeString manually on user input.
 * 2) you store it in CredentialStore
 * 3) you consume it... BUT native credentials store do not store credentials immediately - write is postponed, so, will be an critical error.
 *
 * so, currently - only credentials store implementations should set this flag on get.
 */
@Suppress("EqualsOrHashCode")
class OneTimeString @JvmOverloads constructor(value: CharArray, offset: Int = 0, length: Int = value.size, private var clearable: Boolean = false) : CharArrayCharSequence(value, offset, offset + length) {
  private val consumed = AtomicReference<String?>()

  constructor(value: String): this(value.toCharArray()) {
  }

  private fun consume(willBeCleared: Boolean) {
    if (!clearable) {
      return
    }

    if (!willBeCleared) {
      consumed.get()?.let { throw IllegalStateException("Already consumed: $it\n---\n") }
    }
    else if (!consumed.compareAndSet(null, ExceptionUtil.currentStackTrace())) {
      throw IllegalStateException("Already consumed at ${consumed.get()}")
    }
  }

  fun toString(clear: Boolean = false): String {
    consume(clear)
    val result = super.toString()
    clear()
    return result
  }

  // string will be cleared and not valid after
  @JvmOverloads
  fun toByteArray(clear: Boolean = true): ByteArray {
    consume(clear)

    val result = Charsets.UTF_8.encode(CharBuffer.wrap(myChars, myStart, length))
    if (clear) {
      clear()
    }
    return result.toByteArray()
  }

  private fun clear() {
    if (clearable) {
      myChars.fill('\u0000', myStart, myEnd)
    }
  }

  @JvmOverloads
  fun toCharArray(clear: Boolean = true): CharArray {
    consume(clear)
    if (clear) {
      val result = CharArray(length)
      getChars(result, 0)
      clear()
      return result
    }
    else {
      return chars
    }
  }

  fun clone(clear: Boolean, clearable: Boolean) = OneTimeString(toCharArray(clear), clearable = clearable)

  override fun equals(other: Any?): Boolean {
    if (other is CharSequence) {
      return StringUtil.equals(this, other)
    }
    return super.equals(other)
  }

  fun appendTo(builder: StringBuilder) {
    consume(false)
    builder.append(myChars, myStart, length)
  }
}