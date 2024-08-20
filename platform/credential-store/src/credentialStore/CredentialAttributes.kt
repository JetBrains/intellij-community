// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Contract

const val SERVICE_NAME_PREFIX: String = "IntelliJ Platform"

/**
 * The combined name of your service and name of service that requires authentication.
 *
 * Can be specified in:
 * * a reverse-DNS format: `com.apple.facetime: registrationV1`
 * * a prefixed human-readable format: `IntelliJ Platform Settings Repository — github.com`,
 * where `IntelliJ Platform` prefix **is mandatory**.
 */
fun generateServiceName(subsystem: String, key: String): String = "${SERVICE_NAME_PREFIX} ${subsystem} — ${key}"

/**
 * Consider using [generateServiceName] to generate [serviceName].
 */
data class CredentialAttributes(
  val serviceName: String,
  val userName: String?,
  val isPasswordMemoryOnly: Boolean,
  val cacheDeniedItems: Boolean
) {
  constructor(serviceName: String)
    : this(serviceName, userName = null, isPasswordMemoryOnly = false, cacheDeniedItems = true)
  constructor(serviceName: String, userName: String?)
    : this(serviceName, userName, isPasswordMemoryOnly = false, cacheDeniedItems = true)
  constructor(serviceName: String, userName: String?, isPasswordMemoryOnly: Boolean)
    : this(serviceName, userName, isPasswordMemoryOnly, cacheDeniedItems = true)

  @Deprecated("use `Credentials(serviceName, userName)`", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  constructor(serviceName: String, userName: String?, requestor: Class<*>?)
    : this(serviceName, userName, isPasswordMemoryOnly = false, cacheDeniedItems = true)

  @Deprecated("use `Credentials(serviceName, userName, isPasswordMemoryOnly)`", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  constructor(serviceName: String, userName: String?, requestor: Class<*>?, isPasswordMemoryOnly: Boolean)
    : this(serviceName, userName, isPasswordMemoryOnly, cacheDeniedItems = true)

  @Deprecated("use `Credentials(serviceName, userName, isPasswordMemoryOnly, cacheDeniedItems)`", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  constructor(serviceName: String, userName: String?, requestor: Class<*>?, isPasswordMemoryOnly: Boolean, cacheDeniedItems: Boolean) :
    this(serviceName, userName, isPasswordMemoryOnly, cacheDeniedItems)

  @Deprecated("use one of (service name [, ...]) constructors", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  constructor(serviceName: String, userName: String?, requestor: Class<*>?, isPasswordMemoryOnly: Boolean, i: Int, m: kotlin.jvm.internal.DefaultConstructorMarker?)
    : this(serviceName, userName, isPasswordMemoryOnly, cacheDeniedItems = true)

  @Deprecated("use one of (service name [, ...]) constructors", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  constructor(serviceName: String, userName: String?, requestor: Class<*>?, isPasswordMemoryOnly: Boolean, cacheDeniedItems: Boolean, i: Int, m: kotlin.jvm.internal.DefaultConstructorMarker?) :
    this(serviceName, userName, isPasswordMemoryOnly, cacheDeniedItems)
}

/**
 * Pair of user and password.
 *
 * @param user Account name ("John") or path to SSH key file ("/Users/john/.ssh/id_rsa").
 * @param password Can be empty.
 */
class Credentials(user: String?, val password: OneTimeString?) {
  constructor(user: String?) : this(user, password = null as OneTimeString?)
  constructor(user: String?, password: String?) : this(user, password?.let(::OneTimeString))
  constructor(user: String?, password: CharArray?) : this(user, password?.let { OneTimeString(it) })
  constructor(user: String?, password: ByteArray?) : this(user, password?.let { OneTimeString(password) })

  @Deprecated("use one of (user, password) constructors", level = DeprecationLevel.ERROR)
  @Suppress("unused")
  constructor(user: String?, password: OneTimeString?, i: Int, m: kotlin.jvm.internal.DefaultConstructorMarker?) :
    this(user, password)

  val userName: @NlsSafe String? = user.nullize()

  fun getPasswordAsString(): @NlsSafe String? = password?.toString()

  override fun equals(other: Any?) = other is Credentials && userName == other.userName && password == other.password

  override fun hashCode() = (userName?.hashCode() ?: 0) * 37 + (password?.hashCode() ?: 0)

  override fun toString() = "userName: $userName, password size: ${password?.length ?: 0}"
}

val ACCESS_TO_KEY_CHAIN_DENIED: Credentials = Credentials(user = null, password = null as OneTimeString?)
val CANNOT_UNLOCK_KEYCHAIN: Credentials = Credentials(user = null, password = null as OneTimeString?)

@Contract("null -> false")
fun Credentials?.isFulfilled(): Boolean = this != null && userName != null && !password.isNullOrEmpty()

@Contract("null -> false")
fun Credentials?.hasOnlyUserName(): Boolean = this != null && userName != null && password.isNullOrEmpty()

fun Credentials?.isEmpty(): Boolean = this == null || (userName == null && password.isNullOrEmpty())
