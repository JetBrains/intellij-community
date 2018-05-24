// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.toByteArray
import com.intellij.util.text.CharArrayCharSequence
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Contract
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicReference

const val SERVICE_NAME_PREFIX: String = "IntelliJ Platform"

fun generateServiceName(subsystem: String, key: String): String = "$SERVICE_NAME_PREFIX $subsystem — $key"

/**
 * requestor is deprecated. Never use it in new code.
 */
data class CredentialAttributes @JvmOverloads constructor(val serviceName: String, val userName: String? = null, val requestor: Class<*>? = null, val isPasswordMemoryOnly: Boolean = false)

fun CredentialAttributes.toPasswordStoreable(): CredentialAttributes = if (isPasswordMemoryOnly) CredentialAttributes(serviceName, userName, requestor) else this

// user cannot be empty, but password can be
class Credentials(user: String?, val password: OneTimeString? = null) {
  constructor(user: String?, password: String?) : this(user, password?.let(::OneTimeString))

  constructor(user: String?, password: CharArray?) : this(user, password?.let { OneTimeString(it) })

  constructor(user: String?, password: ByteArray?) : this(user, password?.let { OneTimeString(password) })

  val userName: String? = user.nullize()

  fun getPasswordAsString(): String? = password?.toString()

  override fun equals(other: Any?): Boolean {
    if (other !is Credentials) return false
    return userName == other.userName && password == other.password
  }

  override fun hashCode(): Int = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)
}

@Suppress("FunctionName")
  /**
 * DEPRECATED. Never use it in a new code.
 */
fun CredentialAttributes(requestor: Class<*>, userName: String?): CredentialAttributes = CredentialAttributes(requestor.name, userName, requestor)

@Contract("null -> false")
fun Credentials?.isFulfilled(): Boolean = this != null && userName != null && !password.isNullOrEmpty()

fun Credentials?.hasOnlyUserName(): Boolean = this != null && userName != null && password.isNullOrEmpty()

fun Credentials?.isEmpty(): Boolean = this == null || (userName == null && password.isNullOrEmpty())

/**
 * Tries to get credentials both by `newAttributes` and `oldAttributes`, and if they are available by `oldAttributes` migrates old to new,
 * i.e. removes `oldAttributes` from the credentials store, and adds `newAttributes` instead.
 */
fun getAndMigrateCredentials(oldAttributes: CredentialAttributes, newAttributes: CredentialAttributes): Credentials? {
  val safe = PasswordSafe.instance
  var credentials = safe.get(newAttributes)
  if (credentials == null) {
    credentials = safe.get(oldAttributes)
    if (credentials != null) {
      safe.set(oldAttributes, null)
      safe.set(newAttributes, credentials)
    }
  }
  return credentials
}

@Suppress("FunctionName")
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

  constructor(value: String): this(value.toCharArray())

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

  fun clone(clear: Boolean, clearable: Boolean): OneTimeString = OneTimeString(toCharArray(clear), clearable = clearable)

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