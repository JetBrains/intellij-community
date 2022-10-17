// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Base class for account management application service
 * Accounts are stored in [accountsRepository]
 * Credentials are serialized and stored in [passwordSafe]
 *
 * @see [AccountsListener]
 */
abstract class AccountManagerBase<A : Account, Cred>(private val serviceName: String)
  : AccountManager<A, Cred>, Disposable {

  protected open val passwordSafe
    get() = PasswordSafe.instance

  private val persistentAccounts
    get() = accountsRepository()

  protected abstract fun accountsRepository(): AccountsRepository<A>

  private val listeners = CopyOnWriteArrayList<AccountsListener<A>>()

  private val messageBusConnection by lazy { messageBusConnection() }

  @VisibleForTesting
  protected open fun messageBusConnection() = ApplicationManager.getApplication().messageBus.connect(this)

  final override val accounts: Set<A>
    get() = persistentAccounts.accounts

  private val _accountsState = MutableStateFlow(accounts.associateWith { findCredentials(it) })
  override val accountsState: StateFlow<Map<A, Cred?>> = _accountsState.asStateFlow()

  init {
    messageBusConnection.subscribe(PasswordSafeSettings.TOPIC, object : PasswordSafeSettingsListener {
      override fun credentialStoreCleared() = notifyAllCredentialsChanged()
    })
  }

  override fun updateAccounts(accountsWithCredentials: Map<A, Cred?>) {
    val currentSet = persistentAccounts.accounts
    val removed = currentSet - accountsWithCredentials.keys
    for (account in removed) {
      passwordSafe.set(account.credentialAttributes(), null)
    }
    for ((account, credentials) in accountsWithCredentials) {
      if (credentials != null) {
        passwordSafe.set(account.credentialAttributes(), account.credentials(credentials))
        if (currentSet.contains(account)) notifyCredentialsChanged(account)
      }
    }
    val added = accountsWithCredentials.keys - currentSet
    if (added.isNotEmpty() || removed.isNotEmpty()) {
      persistentAccounts.accounts = accountsWithCredentials.keys
      notifyAccountsChanged(currentSet, accountsWithCredentials.keys)
      LOG.debug("Account list changed to: ${persistentAccounts.accounts}")
    }
  }

  override fun updateAccount(account: A, credentials: Cred) {
    val currentSet = persistentAccounts.accounts
    val newAccount = !currentSet.contains(account)
    if (!newAccount) {
      // remove and add an account to update auxiliary fields
      persistentAccounts.accounts = (currentSet - account) + account
    }
    else {
      persistentAccounts.accounts = currentSet + account
      LOG.debug("Added new account: $account")
    }
    passwordSafe.set(account.credentialAttributes(), account.credentials(credentials))
    LOG.debug((if (credentials == null) "Cleared" else "Updated") + " credentials for account: $account")
    if (!newAccount) {
      notifyCredentialsChanged(account)
    }
    else {
      notifyAccountsChanged(currentSet, persistentAccounts.accounts)
    }
  }

  override fun removeAccount(account: A) {
    val currentSet = persistentAccounts.accounts
    val newSet = currentSet - account
    if (newSet.size != currentSet.size) {
      persistentAccounts.accounts = newSet
      passwordSafe.set(account.credentialAttributes(), null)
      LOG.debug("Removed account: $account")
      notifyAccountsChanged(currentSet, newSet)
    }
  }

  private fun notifyAccountsChanged(old: Set<A>, new: Set<A>) {
    listeners.forEach { it.onAccountListChanged(old, new) }
    _accountsState.value = new.associateWith { findCredentials(it) }
  }

  private fun notifyCredentialsChanged(account: A) {
    listeners.forEach { it.onAccountCredentialsChanged(account) }
    _accountsState.update {
      val copy = it.toMutableMap()
      copy[account] = findCredentials(account)
      copy
    }
  }

  private fun notifyAllCredentialsChanged() {
    val newMap = mutableMapOf<A, Cred?>()
    accounts.forEach { acc ->
      newMap[acc] = findCredentials(acc)
      listeners.forEach { it.onAccountCredentialsChanged(acc) }
    }
    _accountsState.value = newMap
  }

  override fun findCredentials(account: A): Cred? =
    passwordSafe.get(account.credentialAttributes())?.getPasswordAsString()?.let(::deserializeCredentials)

  private fun A.credentialAttributes() = CredentialAttributes(generateServiceName(serviceName, id))

  private fun A.credentials(credentials: Cred?): Credentials? = credentials?.let { Credentials(id, serializeCredentials(it)) }

  protected abstract fun serializeCredentials(credentials: Cred): String
  protected abstract fun deserializeCredentials(credentials: String): Cred

  @Deprecated("replaced with stateFlow", ReplaceWith("accountsState"))
  override fun addListener(disposable: Disposable, listener: AccountsListener<A>) {
    listeners.add(listener)
    Disposer.register(disposable) {
      listeners.remove(listener)
    }
  }

  @VisibleForTesting
  fun addListener(listener: AccountsListener<A>) {
    listeners.add(listener)
  }

  override fun dispose() {}

  companion object {
    private val LOG
      get() = thisLogger()
  }
}