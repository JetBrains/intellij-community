// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.*
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

internal class SimpleAccountsListCellRenderer<A : Account, D : AccountDetails>(
  private val defaultPredicate: (A) -> Boolean,
  private val detailsProvider: LoadingAccountsDetailsProvider<A, D>,
  private val actionsController: AccountsPanelActionsController<A>
) : ListCellRenderer<A>, JPanel() {

  private val accountName = JLabel()

  private val serverName = JLabel()
  private val profilePicture = JLabel()

  private val fullName = JLabel()

  private val loadingError = JLabel()
  private val reloginLink = LinkLabel<Any?>(CollaborationToolsBundle.message("login.link"), null)

  init {
    layout = ListLayout.horizontal()
    border = JBUI.Borders.empty(5, 8)

    val namesPanel = JPanel().apply {
      layout = GridBagLayout()
      border = JBUI.Borders.empty(0, 6, 4, 6)

      val bag = GridBag()
        .setDefaultInsets(JBUI.insetsRight(UIUtil.DEFAULT_HGAP))
        .setDefaultAnchor(GridBagConstraints.WEST)
        .setDefaultFill(GridBagConstraints.VERTICAL)
      add(fullName, bag.nextLine().next())
      add(accountName, bag.next())
      add(loadingError, bag.next())
      add(reloginLink, bag.next())
      add(serverName, bag.nextLine().coverLine())
    }

    add(profilePicture)
    add(namesPanel)
  }

  override fun getListCellRendererComponent(list: JList<out A>,
                                            account: A,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(isSelected, list.hasFocus())

    accountName.apply {
      text = account.name
      setBold(if (getDetails(account)?.name == null) defaultPredicate(account) else false)
      foreground = if (getDetails(account)?.name == null) primaryTextColor else secondaryTextColor
    }
    serverName.apply {
      if (account is ServerAccount) {
        isVisible = true
        text = account.server.toString()
      }
      else {
        isVisible = false
      }
      foreground = secondaryTextColor
    }
    profilePicture.apply {
      icon = detailsProvider.getIcon(account, Avatar.Sizes.ACCOUNT)
    }
    fullName.apply {
      text = getDetails(account)?.name
      setBold(defaultPredicate(account))
      isVisible = getDetails(account)?.name != null
      foreground = primaryTextColor
    }
    loadingError.apply {
      text = getError(account)
      foreground = NamedColorUtil.getErrorForeground()
    }
    reloginLink.apply {
      isVisible = getError(account) != null && needReLogin(account)
      setListener(LinkListener { _, _ ->
        editAccount(list, account)
      }, null)
    }
    return this
  }

  private fun editAccount(parentComponent: JComponent, account: A) = actionsController.editAccount(parentComponent, account)

  private fun getDetails(account: A): D? = detailsProvider.getDetails(account)

  @Nls
  private fun getError(account: A): String? = detailsProvider.getErrorText(account)
  private fun needReLogin(account: A): Boolean = detailsProvider.checkErrorRequiresReLogin(account)

  companion object {
    private fun JLabel.setBold(isBold: Boolean) {
      font = font.deriveFont(if (isBold) font.style or Font.BOLD else font.style and Font.BOLD.inv())
    }
  }
}