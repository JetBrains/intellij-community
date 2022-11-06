// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasswordSafeCredentialsRepository<A : Account, Cred : Any>(
  private val serviceName: String,
  private val mapper: CredentialsMapper<Cred>
) : CredentialsRepository<A, Cred> {

  private val passwordSafe
    get() = PasswordSafe.instance

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