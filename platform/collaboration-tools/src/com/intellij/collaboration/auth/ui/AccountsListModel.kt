// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.ui.codereview.SingleValueModel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.Nullable
import java.awt.Point
import javax.swing.JComponent
import javax.swing.ListModel

interface AccountsListModel<A: Account, Cred> {
  var accounts: Set<A>
  var defaultAccount: A?
  val newCredentials: Map<A, Cred>

  val accountsListModel: ListModel<A>
  val busyStateModel: SingleValueModel<Boolean>

  fun addAccount(parentComponent: JComponent, point: RelativePoint? = null)
  fun editAccount(parentComponent: JComponent, account: A)
  fun clearNewCredentials()

  fun addCredentialsChangeListener(listener: (A) -> Unit)
}