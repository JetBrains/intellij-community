// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AccountUrlAuthenticationFailuresHolderTest {

  @Test
  fun `test not failed on token change`() = runTest(UnconfinedTestDispatcher()) {
    val credsFlow = MutableSharedFlow<String>()
    val accountManager = mock<AccountManager<Account, String>> {
      on(it.getCredentialsFlow(any(), any())).thenReturn(credsFlow)
    }
    val holder = AccountUrlAuthenticationFailuresHolder(this) {
      accountManager
    }

    val account = mock<Account>()
    holder.markFailed(account, "123")
    assertTrue(holder.isFailed(account, "123"))

    credsFlow.emit("newToken")
    assertFalse(holder.isFailed(account, "123"))
  }
}