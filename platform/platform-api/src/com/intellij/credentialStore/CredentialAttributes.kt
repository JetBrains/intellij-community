// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Contract

const val SERVICE_NAME_PREFIX = "IntelliJ Platform"

/**
 * [See documentation](https://github.com/JetBrains/intellij-community/blob/master/platform/credential-store/readme.md#service-name)
 */
fun generateServiceName(subsystem: String, key: String) = "$SERVICE_NAME_PREFIX $subsystem — $key"

/**
 * Consider using [generateServiceName] to generate [serviceName].
 *
 * [requestor] is deprecated (never use it in a new code).
 */
data class CredentialAttributes @JvmOverloads constructor(val serviceName: String,
                                                          val userName: String? = null,
                                                          val requestor: Class<*>? = null,
                                                          val isPasswordMemoryOnly: Boolean = false)

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

  override fun toString() = "userName: $userName, password size: ${password?.length ?: 0}"
}

@Suppress("FunctionName", "DeprecatedCallableAddReplaceWith")
/**
 * @deprecated Never use it in a new code.
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