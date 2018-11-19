// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.util.ArrayUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Contract
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

const val SERVICE_NAME_PREFIX = "IntelliJ Platform"

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

  fun getPasswordAsString() = password?.toString()

  override fun equals(other: Any?): Boolean {
    if (other !is Credentials) return false
    return userName == other.userName && password == other.password
  }

  override fun hashCode() = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)

  override fun toString() = "userName: $userName, password size: ${password?.length ?: 0}"
}

@Suppress("FunctionName", "DeprecatedCallableAddReplaceWith")
/**
 * DEPRECATED. Never use it in a new code.
 */
@Deprecated("Never use it in a new code.")
fun CredentialAttributes(requestor: Class<*>, userName: String?) = CredentialAttributes(requestor.name, userName, requestor)

@Contract("null -> false")
fun Credentials?.isFulfilled() = this != null && userName != null && !password.isNullOrEmpty()

fun Credentials?.hasOnlyUserName() = this != null && userName != null && password.isNullOrEmpty()

fun Credentials?.isEmpty() = this == null || (userName == null && password.isNullOrEmpty())

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