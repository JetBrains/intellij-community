// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

interface CredentialsRepository<A : Account, Cred : Any> {
  /**
   * Attempts to persist credentials to some credential store. If they could not be persisted,
   * the user can be displayed a notification to inform them.
   *
   * @param account The account to store credentials for.
   * @param credentials The actual credentials to store.
   */
  suspend fun persistCredentials(account: A, credentials: Cred?)
  suspend fun retrieveCredentials(account: A): Cred?
}