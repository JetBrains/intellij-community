// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import kotlinx.coroutines.flow.Flow

interface CredentialsRepository<A : Account, Cred : Any> {
  /**
   * Checks whether the account manager can persist credentials.
   * If it cannot, one might need to notify the user of a way to
   * fix this. Returns `true` when the credentials repository is
   * able to write credentials to persistent storage, `false` otherwise.
   */
  val canPersistCredentials: Flow<Boolean>

  /**
   * Attempts to persist credentials to some credential store.
   *
   * @param account The account to store credentials for.
   * @param credentials The actual credentials to store.
   */
  suspend fun persistCredentials(account: A, credentials: Cred?)

  /**
   * Attempts to retrieve credentials from storage for the given account.
   * If credentials stored cannot be retrieved, this function errors. If
   * the credential store does not contain credentials for the given account,
   * this function returns `null`.
   */
  suspend fun retrieveCredentials(account: A): Cred?
}