// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.ui.awt.RelativePoint
import javax.swing.JComponent

/**
 * Allows passing UI action implementation to [AccountsPanelFactory] from outside
 */
interface AccountsPanelActionsController<A : Account> {
  /**
   * [true] if [addAccount] shows a popup with sub-actions, [false] othrwise
   */
  val isAddActionWithPopup: Boolean

  fun addAccount(parentComponent: JComponent, point: RelativePoint? = null)
  fun editAccount(parentComponent: JComponent, account: A)
}
