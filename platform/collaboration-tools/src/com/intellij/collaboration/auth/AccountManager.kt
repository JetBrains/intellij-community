// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow


/**
 * Account management service
 *
 * @param A - account type
 * @param Cred - account credentials
 */
interface AccountManager<A : Account, Cred> {

  /**
   * Set of accounts registered within application
   */
  @get:RequiresEdt
  val accounts: Set<A>

  /**
   * Subscribable set of accounts registered within application
   */
  val accountsState: StateFlow<Map<A, Cred?>>

  /**
   * Add/update account and it's credentials
   */
  @RequiresEdt
  fun updateAccount(account: A, credentials: Cred)

  /**
   * Add/update/remove multiple accounts and their credentials
   * Credentials are not updated if null value is passed
   * Should only be used by a bulk update from settings
   */
  @RequiresEdt
  fun updateAccounts(accountsWithCredentials: Map<A, Cred?>)

  /**
   * Remove an account and clear stored credentials
   * Does nothing if account is not present
   */
  @RequiresEdt
  fun removeAccount(account: A)

  /**
   * Retrieve credentials for account
   */
  @RequiresEdt
  fun findCredentials(account: A): Cred?

  /**
   * Add accounts data listener
   */
  @RequiresEdt
  fun addListener(disposable: Disposable, listener: AccountsListener<A>)
}