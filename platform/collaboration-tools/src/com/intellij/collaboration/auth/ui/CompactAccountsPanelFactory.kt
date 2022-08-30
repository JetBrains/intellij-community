// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.avatar.IconsProvider
import com.intellij.collaboration.ui.findIndex
import com.intellij.collaboration.ui.items
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.ClickListener
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.AccountMenuPopupStep
import com.intellij.util.ui.cloneDialog.AccountsMenuListPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class CompactAccountsPanelFactory<A : Account>(
  private val accountsListModel: ListModel<A>,
  private val detailsLoader: AccountsDetailsLoader<A, *>
) {

  fun create(scope: CoroutineScope,
             defaultAvatarIcon: Icon,
             listAvatarSize: Int,
             popupConfig: PopupConfig<A>): JComponent {
    val detailsMap = mutableMapOf<A, CompletableFuture<AccountsDetailsLoader.Result<*>>>()
    val detailsProvider = LoadedAccountsDetailsProvider { account: A ->
      detailsMap[account]?.getNow(null)
    }
    val avatarIconsProvider = LoadingAvatarIconsProvider(scope, detailsLoader, defaultAvatarIcon) { account: A ->
      val result = detailsMap[account]?.getNow(null) as? AccountsDetailsLoader.Result.Success
      result?.details?.avatarUrl
    }

    val iconRenderer = IconCellRenderer(avatarIconsProvider, listAvatarSize)

    @Suppress("UndesirableClassUsage")
    val accountsList = JList(accountsListModel).apply {
      cellRenderer = iconRenderer
      ClientProperty.put(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(iconRenderer))

      selectionMode = ListSelectionModel.SINGLE_SELECTION
      visibleRowCount = 1
      layoutOrientation = JList.HORIZONTAL_WRAP
    }

    PopupMenuListener(accountsListModel, detailsProvider, avatarIconsProvider, popupConfig).installOn(accountsList)

    loadAccountsDetails(accountsList, detailsLoader, detailsMap)
    return accountsList
  }

  private fun <A : Account> loadAccountsDetails(accountsList: JList<A>,
                                                detailsLoader: AccountsDetailsLoader<A, *>,
                                                resultsMap: MutableMap<A, CompletableFuture<AccountsDetailsLoader.Result<*>>>) {

    val listModel = accountsList.model
    listModel.addListDataListener(object : ListDataListener {
      init {
        if (listModel.size > 0) {
          loadDetails(0, listModel.size - 1)
        }
      }

      override fun intervalAdded(e: ListDataEvent) = loadDetails(e.index0, e.index1)
      override fun contentsChanged(e: ListDataEvent) = loadDetails(e.index0, e.index1)

      override fun intervalRemoved(e: ListDataEvent) {
        val accounts = listModel.items.toSet()
        for (account in resultsMap.keys - accounts) {
          resultsMap.remove(account)?.cancel(true)
        }
      }

      private fun loadDetails(startIdx: Int, endIdx: Int) {
        for (i in startIdx..endIdx) {
          val account = listModel.getElementAt(i)
          resultsMap[account]?.cancel(true)
          val detailsLoadingResult = detailsLoader.loadDetailsAsync(account).asCompletableFuture()
          detailsLoadingResult.handleOnEdt(ModalityState.any()) { _, _ ->
            repaint(account)
          }
          resultsMap[account] = detailsLoadingResult
        }
      }

      private fun repaint(account: A): Boolean {
        val idx = listModel.findIndex(account).takeIf { it >= 0 } ?: return true
        val cellBounds = accountsList.getCellBounds(idx, idx)
        accountsList.repaint(cellBounds)
        return false
      }
    })
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
    private val detailsProvider: AccountsDetailsProvider<A, AccountDetails>,
    private val avatarIconsProvider: IconsProvider<A>,
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
        val avatar = avatarIconsProvider.getIcon(account, popupConfig.avatarSize)
        val showSeparatorAbove = index != 0

        menuItems += AccountMenuItem.Account(accountTitle, serverInfo, avatar, emptyList(), showSeparatorAbove)
      }
      menuItems += popupConfig.createActions()

      AccountsMenuListPopup(null, AccountMenuPopupStep(menuItems)).showUnderneathOf(parentComponent)
    }
  }
}