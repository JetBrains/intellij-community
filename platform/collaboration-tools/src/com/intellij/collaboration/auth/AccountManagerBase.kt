// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Base class for account management application service
 * Accounts are stored in [accountsRepository]
 * Credentials are stored in [credentialsRepository]
 */
abstract class AccountManagerBase<A : Account, Cred : Any>(
  private val logger: Logger
) : AccountManager<A, Cred> {

  private val persistentAccounts get() = accountsRepository()
  protected abstract fun accountsRepository(): AccountsRepository<A>

  private val persistentCredentials get() = credentialsRepository()
  protected abstract fun credentialsRepository(): CredentialsRepository<A, Cred>

  private val _accountsState = MutableStateFlow(persistentAccounts.accounts)
  override val accountsState: StateFlow<Set<A>> = _accountsState.asStateFlow()

  private val accountsEventsFlow = MutableSharedFlow<Event<A, Cred>>()
  private val mutex = Mutex()

  override suspend fun updateAccounts(accountsWithCredentials: Map<A, Cred?>) {
    withContext(Dispatchers.Default) {
      mutex.withLock {
        withContext(NonCancellable) {
          val currentSet = persistentAccounts.accounts
          val removed = currentSet - accountsWithCredentials.keys
          for (account in removed) {
            saveCredentialsSafe(account, null)
          }

          for ((account, credentials) in accountsWithCredentials) {
            if (credentials != null) {
              saveCredentialsSafe(account, credentials)
            }
          }
          val added = accountsWithCredentials.keys - currentSet
          if (added.isNotEmpty() || removed.isNotEmpty()) {
            persistentAccounts.accounts = accountsWithCredentials.keys
            _accountsState.value = accountsWithCredentials.keys
            logger.debug("Account list changed to: ${persistentAccounts.accounts}")
          }
          accountsEventsFlow.emit(Event.AccountsRemoved(removed))
          accountsEventsFlow.emit(Event.AccountsAddedOrUpdated(accountsWithCredentials))
        }
      }
    }
  }

  override suspend fun updateAccount(account: A, credentials: Cred) {
    withContext(Dispatchers.Default) {
      mutex.withLock {
        withContext(NonCancellable) {
          val currentSet = persistentAccounts.accounts
          val newAccount = account !in currentSet
          val newSet = if (!newAccount) {
            // remove and add an account to update auxiliary fields
            (currentSet - account) + account
          }
          else {
            logger.debug("Added new account: $account")
            currentSet + account
          }
          persistentAccounts.accounts = newSet
          saveCredentialsSafe(account, credentials)
          _accountsState.value = newSet
          accountsEventsFlow.emit(Event.AccountsAddedOrUpdated(mapOf(account to credentials)))
          logger.debug("Updated credentials for account: $account")
        }
      }
    }
  }

  override suspend fun removeAccount(account: A) {
    withContext(Dispatchers.Default) {
      mutex.withLock {
        withContext(NonCancellable) {
          val currentSet = persistentAccounts.accounts
          val newSet = currentSet - account
          if (newSet.size != currentSet.size) {
            persistentAccounts.accounts = newSet
            saveCredentialsSafe(account, null)
            _accountsState.value = newSet
            accountsEventsFlow.emit(Event.AccountsRemoved(setOf(account)))
            logger.debug("Removed account: $account")
          }
        }
      }
    }
  }

  private suspend fun saveCredentialsSafe(account: A, credentials: Cred?) {
    withContext(Dispatchers.IO) {
      try {
        persistentCredentials.persistCredentials(account, credentials)
      }
      catch (e: Exception) {
        logger.warn(e)
      }
    }
  }

  override suspend fun findCredentials(account: A): Cred? = persistentCredentials.retrieveCredentials(account)

  override suspend fun getCredentialsState(scope: CoroutineScope, account: A): StateFlow<Cred?> =
    withContext(scope.coroutineContext + Dispatchers.Default) {
      mutex.withLock {
        getCredentialsFlow(account).stateIn(scope, SharingStarted.Eagerly, findCredentials(account))
      }
    }

  override fun getCredentialsFlow(account: A): Flow<Cred?> =
    accountsEventsFlow.transform {
      when (it) {
        is Event.AccountsAddedOrUpdated -> {
          it.map[account]?.let { creds ->
            emit(creds)
          }
        }
        is Event.AccountsRemoved -> {
          if (account in it.accounts) {
            emit(null)
          }
        }
      }
    }.flowOn(Dispatchers.Default)

  private sealed interface Event<A, Cred> {
    class AccountsRemoved<A, Cred>(val accounts: Set<A>) : Event<A, Cred>
    class AccountsAddedOrUpdated<A, Cred>(val map: Map<A, Cred?>) : Event<A, Cred>
  }
}