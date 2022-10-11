// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.collaboration.async.disposingScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Base class for account management application service
 * Accounts are stored in [accountsRepository]
 * Credentials are stored in [credentialsRepository]
 */
abstract class AccountManagerBase<A : Account, Cred : Any>(
  private val logger: Logger
) : AccountManager<A, Cred>, Disposable {

  private val persistentAccounts get() = accountsRepository()
  protected abstract fun accountsRepository(): AccountsRepository<A>

  private val persistentCredentials get() = credentialsRepository()
  protected abstract fun credentialsRepository(): CredentialsRepository<A, Cred>

  private val _accountsState = MutableSharedFlow<Set<A>>()
  override val accountsState: StateFlow<Set<A>> by lazy {
    _accountsState.stateIn(disposingScope(), SharingStarted.Eagerly, persistentAccounts.accounts)
  }

  private val accountsEventsFlow = MutableSharedFlow<Event<A, Cred>>()
  private val mutex = Mutex()

  override fun updateAccounts(accountsWithCredentials: Map<A, Cred?>) {
    runBlocking {
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
            _accountsState.emit(accountsWithCredentials.keys)
            logger.debug("Account list changed to: ${persistentAccounts.accounts}")
          }
          accountsEventsFlow.emit(Event.AccountsRemoved(removed))
          accountsEventsFlow.emit(Event.AccountsAddedOrUpdated(accountsWithCredentials))
        }
      }
    }
  }

  override fun updateAccount(account: A, credentials: Cred) {
    runBlocking {
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
          _accountsState.emit(newSet)
          accountsEventsFlow.emit(Event.AccountsAddedOrUpdated(mapOf(account to credentials)))
          logger.debug("Updated credentials for account: $account")
        }
      }
    }
  }

  override fun removeAccount(account: A) {
    runBlocking {
      mutex.withLock {
        withContext(NonCancellable) {
          val currentSet = persistentAccounts.accounts
          val newSet = currentSet - account
          if (newSet.size != currentSet.size) {
            persistentAccounts.accounts = newSet
            saveCredentialsSafe(account, null)
            _accountsState.emit(newSet)
            accountsEventsFlow.emit(Event.AccountsRemoved(setOf(account)))
            logger.debug("Removed account: $account")
          }
        }
      }
    }
  }

  private suspend fun saveCredentialsSafe(account: A, credentials: Cred?) {
    try {
      persistentCredentials.persistCredentials(account, credentials)
    }
    catch (e: Exception) {
      logger.warn(e)
    }
  }

  override fun findCredentials(account: A): Cred? = runBlocking {
    persistentCredentials.retrieveCredentials(account)
  }

  override fun getCredentialsFlow(account: A, withCurrent: Boolean): Flow<Cred?> = channelFlow {
    mutex.withLock {
      // subscribe to map updates first
      launch(start = CoroutineStart.UNDISPATCHED) {
        accountsEventsFlow.collect {
          when (it) {
            is Event.AccountsAddedOrUpdated -> {
              send(it.map[account])
            }
            is Event.AccountsRemoved -> {
              if (account in it.accounts) {
                cancel()
              }
            }
          }
        }
      }
      if (withCurrent) {
        try {
          send(persistentCredentials.retrieveCredentials(account))
        }
        catch (e: Exception) {
          logger.warn("Failed to retrieve credentials", e)
          send(null)
        }
      }
    }
  }

  override fun dispose() = Unit

  private sealed interface Event<A, Cred> {
    class AccountsRemoved<A, Cred>(val accounts: Set<A>) : Event<A, Cred>
    class AccountsAddedOrUpdated<A, Cred>(val map: Map<A, Cred?>) : Event<A, Cred>
  }
}