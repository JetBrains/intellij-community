// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.items
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ClickListener
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.AccountMenuPopupStep
import com.intellij.util.ui.cloneDialog.AccountsMenuListPopup
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class CompactAccountsPanelFactory<A : Account>(
  private val accountsListModel: ListModel<A>
) {

  fun create(detailsProvider: LoadingAccountsDetailsProvider<A, *>,
             listAvatarSize: Int,
             popupConfig: PopupConfig<A>): JComponent {

    val iconRenderer = IconCellRenderer(detailsProvider, listAvatarSize)

    fun buildTooltipHtml(): String = HtmlBuilder()
      .appendWithSeparators(HtmlChunk.br(), accountsListModel.items.map { HtmlChunk.text(it.name) })
      .toString()

    @Suppress("UndesirableClassUsage")
    val accountsList = JList(accountsListModel).apply {
      isOpaque = false
      cellRenderer = iconRenderer
      ClientProperty.put(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(iconRenderer))

      selectionMode = ListSelectionModel.SINGLE_SELECTION
      visibleRowCount = 1
      layoutOrientation = JList.HORIZONTAL_WRAP
    }

    accountsList.launchOnShow("AccountsListUpdate") {
      val listener = object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent?) = updateAccountsTooltip()
        override fun intervalAdded(e: ListDataEvent?) = updateAccountsTooltip()
        override fun intervalRemoved(e: ListDataEvent?) = updateAccountsTooltip()
        fun updateAccountsTooltip() {
          accountsList.toolTipText = buildTooltipHtml()
        }
      }
      accountsListModel.addListDataListener(listener)
      listener.updateAccountsTooltip()
      try {
        awaitCancellation()
      }
      finally {
        accountsListModel.removeListDataListener(listener)
      }
    }
    PopupMenuListener(accountsListModel, detailsProvider, popupConfig).installOn(accountsList)
    return accountsList
  }

  private class IconCellRenderer<A : Account>(
    private val iconsProvider: IconsProvider<A>,
    private val avatarSize: Int
  ) : ListCellRenderer<A>, JLabel() {

    override fun getListCellRendererComponent(list: JList<out A>?,
                                              value: A,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      icon = iconsProvider.getIcon(value, avatarSize)
      return this
    }
  }

  interface PopupConfig<A : Account> {
    val avatarSize: Int
    fun createActions(): Collection<AccountMenuItem.Action>
  }

  private class PopupMenuListener<A : Account>(
    private val model: ListModel<A>,
    private val detailsProvider: LoadingAccountsDetailsProvider<A, *>,
    private val popupConfig: PopupConfig<A>
  ) : ClickListener() {

    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
      val parentComponent = event.source
      if (parentComponent !is JComponent) return false
      showPopupMenu(parentComponent)
      return true
    }

    private fun showPopupMenu(parentComponent: JComponent) {
      val menuItems = mutableListOf<AccountMenuItem>()

      for ((index, account) in model.items.withIndex()) {
        val accountTitle = detailsProvider.getDetails(account)?.name ?: account.name
        val serverInfo = if (account is ServerAccount) CollaborationToolsUIUtil.cleanupUrl(account.server.toString()) else ""
        val avatar = detailsProvider.getIcon(account, popupConfig.avatarSize)
        val showSeparatorAbove = index != 0

        menuItems += AccountMenuItem.Account(accountTitle, serverInfo, avatar, emptyList(), showSeparatorAbove)
      }
      menuItems += popupConfig.createActions()

      AccountsMenuListPopup(null, AccountMenuPopupStep(menuItems)).showUnderneathOf(parentComponent)
    }
  }
}