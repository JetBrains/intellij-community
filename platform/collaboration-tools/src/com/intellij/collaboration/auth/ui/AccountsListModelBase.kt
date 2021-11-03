// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.ui.CollectionListModel
import java.util.concurrent.CopyOnWriteArrayList

abstract class AccountsListModelBase<A : Account, Cred> : AccountsListModel<A, Cred> {
  override var accounts: Set<A>
    get() = accountsListModel.items.toSet()
    set(value) {
      accountsListModel.removeAll()
      accountsListModel.add(value.toList())
    }
  override var selectedAccount: A? = null
  override val newCredentials = mutableMapOf<A, Cred>()

  override val accountsListModel = CollectionListModel<A>()
  override val busyStateModel = SingleValueModel(false)

  private val credentialsChangesListeners = CopyOnWriteArrayList<(A) -> Unit>()

  override fun clearNewCredentials() = newCredentials.clear()

  protected fun notifyCredentialsChanged(account: A) {
    credentialsChangesListeners.forEach { it(account) }
    accountsListModel.contentsChanged(account)
  }

  final override fun addCredentialsChangeListener(listener: (A) -> Unit) {
    credentialsChangesListeners.add(listener)
  }
}
