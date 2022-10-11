// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

interface CredentialsRepository<A : Account, Cred : Any> {
  suspend fun persistCredentials(account: A, credentials: Cred?)
  suspend fun retrieveCredentials(account: A): Cred?
}