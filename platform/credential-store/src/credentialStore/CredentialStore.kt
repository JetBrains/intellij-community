// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore

import com.intellij.util.Ephemeral
import com.intellij.util.StaticEphemeral
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.runAsync

/**
 * Please see [Storing Sensitive Data](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html).
 */
interface CredentialStore {
  operator fun get(attributes: CredentialAttributes): Credentials?

  fun getPassword(attributes: CredentialAttributes): String? {
    val credentials = get(attributes)
    return credentials?.getPasswordAsString()
  }

  @ApiStatus.Experimental
  suspend fun getAsync(attributes: CredentialAttributes): Ephemeral<Credentials> =
    ephemeral(runAsync { get(attributes) }.await() )

  @ApiStatus.Experimental
  suspend fun <T : Any> ephemeral(value: T?): Ephemeral<T> =
    StaticEphemeral(value)

  operator fun set(attributes: CredentialAttributes, credentials: Credentials?)

  fun setPassword(attributes: CredentialAttributes, password: String?) {
    set(attributes, password?.let { Credentials(attributes.userName, it) })
  }
}
