// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Generic [CredentialsRepository] that stores account credentials through the
 * IDE-wide password safe. This safe is subject to platform changes and is
 * configurable by the user. If the user has not configured it, the default is
 * to try to use the system keychain. Whether this credentials repository can
 * persist credentials is thus dependent on the user's settings for password
 * safes.
 */
class PasswordSafeCredentialsRepository<A : Account, Cred : Any>(
  private val serviceName: String,
  private val mapper: CredentialsMapper<Cred>
) : CredentialsRepository<A, Cred> {
  private val passwordSafe
    get() = PasswordSafe.instance

  // It is assumed all options other than MEMORY_ONLY persist to disk in some way.
  override val canPersistCredentials: Flow<Boolean> =
    callbackFlow {
      trySend(!passwordSafe.isMemoryOnly)
      ApplicationManager.getApplication().messageBus.connect(this)
        .subscribe(PasswordSafeSettings.TOPIC, object : PasswordSafeSettingsListener {
          override fun typeChanged(oldValue: ProviderType, newValue: ProviderType) {
            trySend(newValue != ProviderType.MEMORY_ONLY)
          }
        })
      awaitClose()
    }

  override suspend fun persistCredentials(account: A, credentials: Cred?) {
    withContext(Dispatchers.IO) {
      passwordSafe.set(account.credentialAttributes(), account.credentials(credentials))
    }
  }

  override suspend fun retrieveCredentials(account: A): Cred? =
    withContext(Dispatchers.IO) {
      passwordSafe.get(account.credentialAttributes())
        ?.getPasswordAsString()
        ?.let(mapper::deserialize)
    }

  private fun A.credentialAttributes() = CredentialAttributes(generateServiceName(serviceName, id))

  private fun A.credentials(credentials: Cred?): Credentials? =
    credentials?.let { Credentials(id, mapper.serialize(it)) }

  interface CredentialsMapper<Cred : Any> {
    fun serialize(credentials: Cred): String
    fun deserialize(credentials: String): Cred

    object Simple : CredentialsMapper<String> {
      override fun serialize(credentials: String): String = credentials
      override fun deserialize(credentials: String): String = credentials
    }
  }
}