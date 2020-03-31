// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Contract

const val SERVICE_NAME_PREFIX = "IntelliJ Platform"

/**
 * The combined name of your service and name of service that requires authentication.
 *
 * Can be specified in:
 * * reverse-DNS format: `com.apple.facetime: registrationV1`
 * * prefixed human-readable format: `IntelliJ Platform Settings Repository — github.com`, where `IntelliJ Platform` is a required prefix. **You must always use this prefix**.
 */
fun generateServiceName(subsystem: String, key: String) = "$SERVICE_NAME_PREFIX $subsystem — $key"

/**
 * Consider using [generateServiceName] to generate [serviceName].
 *
 * [requestor] is deprecated (never use it in a new code).
 */
data class CredentialAttributes(
  val serviceName: String,
  val userName: String? = null,
  val requestor: Class<*>? = null,
  val isPasswordMemoryOnly: Boolean = false,
  val cacheDeniedItems: Boolean = true
) {
  @JvmOverloads
  constructor(serviceName: String,
              userName: String? = null,
              requestor: Class<*>? = null,
              isPasswordMemoryOnly: Boolean = false)
    : this(serviceName, userName, requestor, isPasswordMemoryOnly, true)
}

/**
 * Pair of user and password.
 *
 * @param user Account name ("John") or path to SSH key file ("/Users/john/.ssh/id_rsa").
 * @param password Can be empty.
 */
class Credentials(user: String?, val password: OneTimeString? = null) {
  constructor(user: String?, password: String?) : this(user, password?.let(::OneTimeString))

  constructor(user: String?, password: CharArray?) : this(user, password?.let { OneTimeString(it) })

  constructor(user: String?, password: ByteArray?) : this(user, password?.let { OneTimeString(password) })

  val userName = user.nullize()

  fun getPasswordAsString() = password?.toString()

  override fun equals(other: Any?) = other is Credentials && userName == other.userName && password == other.password

  override fun hashCode() = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)

  override fun toString() = "userName: $userName, password size: ${password?.length ?: 0}"
}

/** @deprecated Use [CredentialAttributes] instead. */
@Deprecated("Never use it in a new code.")
@Suppress("FunctionName", "DeprecatedCallableAddReplaceWith")
fun CredentialAttributes(requestor: Class<*>, userName: String?) = CredentialAttributes(requestor.name, userName, requestor)

@Contract("null -> false")
fun Credentials?.isFulfilled() = this != null && userName != null && !password.isNullOrEmpty()

@Contract("null -> false")
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