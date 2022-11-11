// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.util.messages.MessageBusConnection
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.*

@RunWith(JUnit4::class)
class AccountManagerBaseTest {

  private val account = MockAccount()
  private val account2 = MockAccount()

  private lateinit var accountsRepository: AccountsRepository<MockAccount>
  private lateinit var mockPasswordSafe: PasswordSafe
  private lateinit var accountsListener: AccountsListener<MockAccount>

  private lateinit var manager: AccountManager<MockAccount, String>

  @Before
  fun createMocks() {
    accountsRepository = object : AccountsRepository<MockAccount> {
      override var accounts: Set<MockAccount> = setOf()
    }
    mockPasswordSafe = mock(PasswordSafe::class.java)
    @Suppress("UNCHECKED_CAST")
    accountsListener = mock(AccountsListener::class.java) as AccountsListener<MockAccount>

    manager = object : AccountManagerBase<MockAccount, String>("test") {
      override fun accountsRepository() = accountsRepository
      override val passwordSafe = mockPasswordSafe

      override fun messageBusConnection(): MessageBusConnection = mock(MessageBusConnection::class.java)
      override fun serializeCredentials(credentials: String): String = credentials
      override fun deserializeCredentials(credentials: String): String = credentials
    }.apply {
      addListener(accountsListener)
    }
  }

  @Test
  fun `test account list manipulations`() {
    manager.updateAccount(account, "test")
    assert(manager.accounts.size == 1)
    manager.updateAccount(account, "test")
    assert(manager.accounts.size == 1)
    manager.removeAccount(account)
    assert(manager.accounts.isEmpty())

    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    assert(manager.accounts.size == 2)
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    assert(manager.accounts.size == 2)
    manager.updateAccount(account, "test")
    assert(manager.accounts.size == 2)
    manager.removeAccount(account)
    assert(manager.accounts.size == 1)
    manager.removeAccount(account2)
    assert(manager.accounts.isEmpty())
  }

  @Test
  fun `test notification on add`() {
    manager.updateAccount(account, "test")
    verify(accountsListener).onAccountListChanged(anyCollection(), anyCollection())
    verify(accountsListener, never()).onAccountCredentialsChanged(account)
  }

  @Test
  fun `test notification on update`() {
    manager.updateAccount(account, "test")
    manager.updateAccount(account, "test")
    verify(accountsListener, times(1)).onAccountListChanged(anyCollection(), anyCollection())
    verify(accountsListener).onAccountCredentialsChanged(account)
  }

  @Test
  fun `test notification on remove`() {
    manager.updateAccount(account, "test")
    manager.removeAccount(account)
    verify(accountsListener, times(2)).onAccountListChanged(anyCollection(), anyCollection())
    verify(accountsListener, never()).onAccountCredentialsChanged(account)
  }

  @Test
  fun `test notification on bulk add`() {
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    verify(accountsListener).onAccountListChanged(anyCollection(), anyCollection())
    verify(accountsListener, never()).onAccountCredentialsChanged(account)
  }

  @Test
  fun `test notification on bulk update`() {
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    verify(accountsListener, times(1)).onAccountListChanged(anyCollection(), anyCollection())
    verify(accountsListener, times(1)).onAccountCredentialsChanged(account)
  }

  @Test
  fun `test notification on bulk remove`() {
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    manager.updateAccounts(mapOf())
    verify(accountsListener, times(2)).onAccountListChanged(anyCollection(), anyCollection())
    verify(accountsListener, never()).onAccountCredentialsChanged(account)
  }

  @Test
  fun `test credentials not cleared on bulk update`() {
    manager.updateAccounts(mapOf(account to "test", account2 to "test"))
    manager.updateAccounts(mapOf(account to null, account2 to null))
    verify(mockPasswordSafe, times(2)).set(any(), any())
  }

  @Test
  fun `test account usable after notified`() {
    `when`(accountsListener.onAccountListChanged(anyCollection(), anyCollection())).then {
      verify(mockPasswordSafe, atLeast(1)).set(any(), any())
    }
    manager.updateAccount(account, "test")
  }

  private class MockAccount(override val id: String = generateId()) : Account() {
    override val name: String = ""
  }
}