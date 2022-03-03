// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.avatar.CachingAvatarIconsProvider
import com.intellij.ui.ScalingAsyncImageIcon
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.IconUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import java.util.concurrent.CompletableFuture
import javax.swing.*

class SimpleAccountsListCellRenderer<A : Account, D : AccountDetails>(
  private val listModel: AccountsListModel<A, *>,
  private val detailsProvider: AccountsDetailsProvider<A, D>,
  private val defaultAvatarIcon: Icon
) : ListCellRenderer<A>, JPanel() {

  private val avatarIcons = mutableMapOf<A, Icon>()

  private val accountName = JLabel()

  private val serverName = JLabel()
  private val profilePicture = JLabel()

  private val fullName = JLabel()

  private val loadingError = JLabel()
  private val reloginLink = LinkLabel<Any?>(CollaborationToolsBundle.message("login.link"), null)

  init {
    layout = FlowLayout(FlowLayout.LEFT, 0, 0)
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
    val secondaryTextColor = ListUiUtil.WithTallRow.secondaryForeground(list, isSelected)

    accountName.apply {
      text = account.name
      setBold(if (getDetails(account)?.name == null) isDefault(account) else false)
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
      icon = getAvatarIcon(account)
    }
    fullName.apply {
      text = getDetails(account)?.name
      setBold(isDefault(account))
      isVisible = getDetails(account)?.name != null
      foreground = primaryTextColor
    }
    loadingError.apply {
      text = getError(account)
      foreground = UIUtil.getErrorForeground()
    }
    reloginLink.apply {
      isVisible = getError(account) != null && needReLogin(account)
      setListener(LinkListener { _, _ ->
        editAccount(list, account)
      }, null)
    }
    return this
  }

  private fun getAvatarIcon(account: A): Icon {
    val image = getAvatarImage(account)
    val iconSize = 40
    if (image == null) return IconUtil.resizeSquared(defaultAvatarIcon, iconSize)
    return avatarIcons.getOrPut(account) {
      ScalingAsyncImageIcon(
        iconSize,
        defaultAvatarIcon,
        imageLoader = {
          CompletableFuture<Image?>().completeAsync({
            getAvatarImage(account)
          }, CachingAvatarIconsProvider.avatarLoadingExecutor)
        }
      )
    }
  }

  private fun isDefault(account: A): Boolean = (listModel is AccountsListModel.WithDefault) && account == listModel.defaultAccount
  private fun editAccount(parentComponent: JComponent, account: A) = listModel.editAccount(parentComponent, account)

  private fun getDetails(account: A): D? = detailsProvider.getDetails(account)
  private fun getAvatarImage(account: A): Image? = detailsProvider.getAvatarImage(account)

  @Nls
  private fun getError(account: A): String? = detailsProvider.getErrorText(account)
  private fun needReLogin(account: A): Boolean = detailsProvider.checkErrorRequiresReLogin(account)

  companion object {
    private fun JLabel.setBold(isBold: Boolean) {
      font = font.deriveFont(if (isBold) font.style or Font.BOLD else font.style and Font.BOLD.inv())
    }
  }
}