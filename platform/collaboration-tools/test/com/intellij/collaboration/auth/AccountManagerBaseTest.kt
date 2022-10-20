// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class AccountManagerBaseTest {

  private val accountsRepository = object : AccountsRepository<MockAccount> {
    override var accounts: Set<MockAccount> = emptySet()
  }

  private val credentialsRepository = object : CredentialsRepository<MockAccount, String> {
    private val map = mutableMapOf<MockAccount, String?>()

    override suspend fun persistCredentials(account: MockAccount, credentials: String?) {
      map[account] = credentials
    }

    override suspend fun retrieveCredentials(account: MockAccount): String? = map[account]
  }

  private lateinit var manager: AccountManagerBase<MockAccount, String>

  private val account = MockAccount()
  private val account2 = MockAccount()

  @Before
  fun setUp() {
    manager = object : AccountManagerBase<MockAccount, String>(mock()) {
      override fun accountsRepository() = accountsRepository
      override fun credentialsRepository() = credentialsRepository
    }
  }

  @After
  fun tearDown() {
    Disposer.dispose(manager)
  }

  @Test
  fun `test list state`() = runTest {
    val state = manager.accountsState
    manager.updateAccount(account, "test")
    assertEquals(1, state.value.size)
    manager.updateAccount(account, "test")
    assertEquals(1, state.value.size)
    manager.removeAccount(account)
    assertTrue(state.value.isEmpty())

    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    assertEquals(2, state.value.size)
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    assertEquals(2, state.value.size)
    manager.updateAccount(account, "test")
    assertEquals(2, state.value.size)
    manager.removeAccount(account)
    assertEquals(1, state.value.size)
    manager.removeAccount(account2)
    assertTrue(state.value.isEmpty())
  }

  @Test
  fun `test account usable after addition`() = runTest {
    manager.updateAccount(account, "test")
    assertNotNull(manager.findCredentials(account))
  }

  private class MockAccount(override val id: String = generateId()) : Account() {
    override val name: String = ""
  }
}