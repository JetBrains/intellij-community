// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import javax.swing.ListModel

interface AccountsListModel<A: Account, Cred> {
  var accounts: Set<A>
  var selectedAccount: A?
  val newCredentials: Map<A, Cred>

  val accountsListModel: ListModel<A>

  fun clearNewCredentials()

  interface WithDefault<A: Account, Cred>: AccountsListModel<A, Cred> {
    var defaultAccount: A?
  }
}
