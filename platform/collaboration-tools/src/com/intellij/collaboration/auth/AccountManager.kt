// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Account management service
 *
 * @param A - account type
 * @param Cred - account credentials
 */
interface AccountManager<A : Account, Cred> {

  /**
   * Subscribable set of accounts registered within application
   */
  val accountsState: StateFlow<Set<A>>

  /**
   * Add/update account and it's credentials
   */
  suspend fun updateAccount(account: A, credentials: Cred)

  /**
   * Add/update/remove multiple accounts and their credentials.
   * Credentials are not updated if null value is passed
   * Should only be used by a bulk update from settings
   */
  suspend fun updateAccounts(accountsWithCredentials: Map<A, Cred?>)

  /**
   * Remove an account and clear stored credentials
   * Does nothing if account is not present
   */
  suspend fun removeAccount(account: A)

  /**
   * Retrieve credentials for account
   */
  suspend fun findCredentials(account: A): Cred?

  /**
   * Flow of account credentials
   * Will be closed when the account is removed
   */
  fun getCredentialsFlow(account: A, withCurrent: Boolean = true): Flow<Cred?>
}