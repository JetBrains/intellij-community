// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.ui.CollectionListModel

abstract class MutableAccountsListModel<A : Account, Cred>
  : AccountsListModel<A, Cred> {

  override var accounts: Set<A>
    get() = accountsListModel.items.toSet()
    set(value) {
      accountsListModel.removeAll()
      accountsListModel.add(value.toList())
    }
  override var selectedAccount: A? = null
  override val newCredentials = mutableMapOf<A, Cred>()

  override val accountsListModel = CollectionListModel<A>()

  override fun clearNewCredentials() = newCredentials.clear()

  fun add(account: A, cred: Cred) {
    accountsListModel.add(account)
    newCredentials[account] = cred
    notifyCredentialsChanged(account)
  }

  fun update(account: A, cred: Cred) {
    newCredentials[account] = cred
    notifyCredentialsChanged(account)
  }

  private fun notifyCredentialsChanged(account: A) {
    accountsListModel.contentsChanged(account)
  }
}
