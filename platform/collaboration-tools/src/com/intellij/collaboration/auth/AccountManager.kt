// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.application.subscribe
import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Handles application-level accounts
 *
 * @param A - account type
 * @param Cred - account credentials
 */
abstract class AccountManager<A : Account, Cred>(private val serviceName: String)
  : Disposable {

  private val LOG = thisLogger()

  protected abstract val persistentAccounts: AccountsPersistentStateComponent<A, *>

  var accounts: Set<A>
    get() = persistentAccounts.accounts
    set(value) {
      val oldValue = persistentAccounts.accounts
      persistentAccounts.accounts = value
      oldValue.filter { it !in value }.forEach {
        setCredentials(it, null)
        fireAccountRemoved(it)
      }
      LOG.debug("Account list changed to: $value")
    }

  init {
    PasswordSafeSettings.TOPIC.subscribe(this, PasswordStorageClearedListener())
  }

  /**
   * Add/update/remove account credentials from application
   */
  fun setCredentials(account: A, credentials: Cred?) {
    PasswordSafe.instance.set(createCredentialAttributes(account.id), credentials?.let { createCredentials(account.id, it) })
    LOG.debug((if (credentials == null) "Cleared" else "Updated") + " credentials for account: $account")

    fireCredentialsChanged(account)
  }

  /**
   * Retrieve credentials for account from password safe
   */
  fun findCredentials(account: A): Cred? =
    PasswordSafe.instance.get(createCredentialAttributes(account.id))?.getPasswordAsString()?.let(::deserializeCredentials)

  protected abstract fun fireAccountRemoved(account: A)

  protected abstract fun fireCredentialsChanged(account: A)

  private fun createCredentialAttributes(accountId: String) = CredentialAttributes(createServiceName(accountId))

  private fun createServiceName(accountId: String): String = generateServiceName(serviceName, accountId)

  private fun createCredentials(accountId: String, credentials: Cred?) =
    Credentials(accountId, credentials?.let(::serializeCredentials))

  protected abstract fun serializeCredentials(credentials: Cred): String

  protected abstract fun deserializeCredentials(credentials: String): Cred

  override fun dispose() {}

  private inner class PasswordStorageClearedListener : PasswordSafeSettingsListener {
    override fun credentialStoreCleared() {
      accounts.forEach { fireCredentialsChanged(it) }
    }
  }
}