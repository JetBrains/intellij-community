// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth

import com.intellij.collaboration.api.ServerPath
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.util.Disposer
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

  private lateinit var persistentAccounts: AccountsPersistentStateComponent<MockAccount, *>
  private lateinit var passwordSafe: PasswordSafe
  private lateinit var accountsListener: AccountsListener<MockAccount>
  private lateinit var manager: TestManager

  @Before
  fun createMocks() {
    persistentAccounts = object : SimpleAccountsPersistentStateComponent<MockAccount>() {}
    passwordSafe = mock(PasswordSafe::class.java)
    @Suppress("UNCHECKED_CAST")
    accountsListener = mock(AccountsListener::class.java) as AccountsListener<MockAccount>
    manager = TestManager(persistentAccounts, passwordSafe).apply {
      addListener(Disposer.newDisposable(), accountsListener)
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
    verify(passwordSafe, times(2)).set(any(), any())
  }

  @Test
  fun `test account usable after notified`() {
    `when`(accountsListener.onAccountListChanged(anyCollection(), anyCollection())).then {
      verify(passwordSafe, atLeast(1)).set(any(), any())
    }
    manager.updateAccount(account, "test")
  }

  private class MockAccount(override val id: String = generateId()) : Account() {
    override val name: String = ""
    override val server: ServerPath = object : ServerPath {
      override fun toString() = "test_server"
    }
  }

  private class TestManager(private val persistentAccounts: AccountsPersistentStateComponent<MockAccount, *>,
                            override val passwordSafe: PasswordSafe)
    : AccountManagerBase<MockAccount, String>("test") {
    override fun persistentAccounts() = persistentAccounts
    override fun messageBusConnection(): MessageBusConnection = mock(MessageBusConnection::class.java)
    override fun serializeCredentials(credentials: String): String = credentials
    override fun deserializeCredentials(credentials: String): String = credentials
  }
}