// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class PersistentDefaultAccountHolderTest {
  private val account1: TestAccount = TestAccount("user")

  @Test
  fun `default account is not updated if unknown by AccountManager`() = runTest {
    val accountManager = setupMockAccountManager(setOf())

    val dahScope = backgroundScope.childScope("DefaultAccountHolderScope")
    val defaultAccountHolder = dahScope.setupDefaultAccountHolder(accountManager)

    defaultAccountHolder.account = account1

    assertNull(defaultAccountHolder.account)
  }

  @Test
  fun `default account is updated if known by AccountManager`() = runTest {
    val accountManager = setupMockAccountManager(setOf(account1))

    val dahScope = backgroundScope.childScope("DefaultAccountHolderScope")
    val defaultAccountHolder = dahScope.setupDefaultAccountHolder(accountManager)

    defaultAccountHolder.account = account1

    assertEquals(account1, defaultAccountHolder.account)
  }

  @Test
  @OptIn(ExperimentalCoroutinesApi::class)
  fun `default account is cleared if no longer known by AccountManager`() = runTest {
    val accountFlow = MutableStateFlow(setOf(account1))
    val accountManager = setupMockAccountManager(accountFlow)

    // create the scope with `UnconfinedTestDispatcher` to make sure flow updates are applied immediately
    val dahScope = backgroundScope.childScope("DefaultAccountHolderScope", UnconfinedTestDispatcher(testScheduler))
    val defaultAccountHolder = dahScope.setupDefaultAccountHolder(accountManager)

    // first set the account (should be fine)
    defaultAccountHolder.account = account1

    // then update the accounts-state (should clear the account value)
    accountFlow.value = emptySet()

    assertNull(defaultAccountHolder.account)
  }

  @Test
  fun `default account is cleared if no longer known by AccountManager when saving`() = runTest {
    val accountFlow = MutableStateFlow(setOf(account1))
    val accountManager = setupMockAccountManager(accountFlow)

    val dahScope = backgroundScope.childScope("DefaultAccountHolderScope")
    val defaultAccountHolder = dahScope.setupDefaultAccountHolder(accountManager)

    // first set the account (should be fine), then remove the account from known accounts
    defaultAccountHolder.account = account1

    accountFlow.value = emptySet()
    // do not advance to ensure the timing is: accountFlow updates, dispatches coroutine,
    // but it's not run yet because this body doesn't yield.
    // Hence, the `getState` filtering has to be used.

    assertNull(defaultAccountHolder.state.defaultAccountId)
  }

  private fun CoroutineScope.setupDefaultAccountHolder(accountManager: AccountManager<TestAccount, String>)
    : PersistentDefaultAccountHolder<TestAccount> =
    object : PersistentDefaultAccountHolder<TestAccount>(Mockito.mock<Project>(), this) {
      override fun accountManager(): AccountManager<TestAccount, *> = accountManager

      override fun notifyDefaultAccountMissing() {
      }
    }

  private fun setupMockAccountManager(accounts: Set<TestAccount>): AccountManagerBase<TestAccount, String> =
    setupMockAccountManager(MutableStateFlow(accounts))

  private fun setupMockAccountManager(accounts: StateFlow<Set<TestAccount>>): AccountManagerBase<TestAccount, String> =
    Mockito.mock<AccountManagerBase<TestAccount, String>>().apply {
      `when`(this.accountsState).thenReturn(accounts)
    }

  private data class TestAccount(override val id: String) : Account() {
    override val name: String = id
  }
}