// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.async.CompletableFutureUtil.handleOnEdt
import com.intellij.collaboration.async.DisposingMainScope
import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.AccountsListener
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.findIndex
import com.intellij.collaboration.ui.items
import com.intellij.collaboration.ui.util.JListHoveredRowMaterialiser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.properties.Delegates.observable

class AccountsPanelFactory<A : Account, Cred>
private constructor(disposable: Disposable,
                    private val accountManager: AccountManager<A, Cred>,
                    private val defaultAccountHolder: DefaultAccountHolder<A>?,
                    private val accountsModel: AccountsListModel<A, Cred>,
                    private val detailsLoader: AccountsDetailsLoader<A, *>) {

  constructor(accountManager: AccountManager<A, Cred>,
              defaultAccountHolder: DefaultAccountHolder<A>,
              accountsModel: AccountsListModel.WithDefault<A, Cred>,
              detailsLoader: AccountsDetailsLoader<A, *>,
              disposable: Disposable) : this(disposable, accountManager, defaultAccountHolder, accountsModel, detailsLoader)

  constructor(accountManager: AccountManager<A, Cred>,
              accountsModel: AccountsListModel<A, Cred>,
              detailsLoader: AccountsDetailsLoader<A, *>,
              disposable: Disposable) : this(disposable, accountManager, null, accountsModel, detailsLoader)

  init {
    DisposingMainScope(disposable).launch {
      accountManager.accountsState.collect {
        if (!isModified()) reset()
      }
    }
  }

  fun accountsPanelCell(row: Row, needAddBtnWithDropdown: Boolean, defaultAvatarIcon: Icon = EmptyIcon.ICON_16): Cell<JComponent> {
    val detailsMap = mutableMapOf<A, CompletableFuture<AccountsDetailsLoader.Result<*>>>()
    val detailsProvider = LoadedAccountsDetailsProvider { account: A ->
      detailsMap[account]?.getNow(null)
    }
    val avatarIconsProvider = LoadingAvatarIconsProvider(detailsLoader, defaultAvatarIcon) { account: A ->
      val result = detailsMap[account]?.getNow(null) as? AccountsDetailsLoader.Result.Success
      result?.details?.avatarUrl
    }

    val accountsList = createList {
      SimpleAccountsListCellRenderer(accountsModel, detailsProvider, avatarIconsProvider)
    }
    loadAccountsDetails(accountsList, detailsLoader, detailsMap)

    val component = wrapWithToolbar(accountsList, needAddBtnWithDropdown)

    return row.cell(component)
      .onIsModified(::isModified)
      .onReset(::reset)
      .onApply(::apply)
  }

  private fun isModified(): Boolean {
    val defaultModified = if (defaultAccountHolder != null && accountsModel is AccountsListModel.WithDefault) {
      accountsModel.defaultAccount != defaultAccountHolder.account
    }
    else false

    return accountsModel.newCredentials.isNotEmpty()
           || accountsModel.accounts != accountManager.accounts
           || defaultModified
  }

  private fun reset() {
    accountsModel.accounts = accountManager.accounts
    if (defaultAccountHolder != null && accountsModel is AccountsListModel.WithDefault) {
      accountsModel.defaultAccount = defaultAccountHolder.account
    }
    accountsModel.clearNewCredentials()
  }

  private fun apply() {
    val newTokensMap = mutableMapOf<A, Cred?>()
    newTokensMap.putAll(accountsModel.newCredentials)
    for (account in accountsModel.accounts) {
      newTokensMap.putIfAbsent(account, null)
    }
    accountManager.updateAccounts(newTokensMap)
    accountsModel.clearNewCredentials()

    if (defaultAccountHolder != null && accountsModel is AccountsListModel.WithDefault) {
      val defaultAccount = accountsModel.defaultAccount
      defaultAccountHolder.account = defaultAccount
    }
  }

  private fun <R> createList(listCellRendererFactory: () -> R): JBList<A> where R : ListCellRenderer<A>, R : JComponent {

    val accountsList = JBList(accountsModel.accountsListModel).apply {
      val renderer = listCellRendererFactory()
      cellRenderer = renderer
      JListHoveredRowMaterialiser.install(this, listCellRendererFactory())
      UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

      selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    accountsList.addListSelectionListener { accountsModel.selectedAccount = accountsList.selectedValue }

    accountsList.emptyText.apply {
      appendText(CollaborationToolsBundle.message("accounts.none.added"))
      appendSecondaryText(CollaborationToolsBundle.message("accounts.add.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        val event = it.source
        val relativePoint = if (event is MouseEvent) RelativePoint(event) else null
        accountsModel.addAccount(accountsList, relativePoint)
      }
      appendSecondaryText(" (${KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNew())})", StatusText.DEFAULT_ATTRIBUTES, null)
    }
    return accountsList
  }

  private fun wrapWithToolbar(accountsList: JBList<A>, needAddBtnWithDropdown: Boolean): JPanel {
    val addIcon: Icon = if (needAddBtnWithDropdown) LayeredIcon.ADD_WITH_DROPDOWN else AllIcons.General.Add

    val toolbar = ToolbarDecorator.createDecorator(accountsList)
      .disableUpDownActions()
      .setAddAction { accountsModel.addAccount(accountsList, it.preferredPopupPoint) }
      .setAddIcon(addIcon)

    if (accountsModel is AccountsListModel.WithDefault) {
      toolbar.addExtraAction(object : ToolbarDecorator.ElementActionButton(CollaborationToolsBundle.message("accounts.set.default"),
                                                                           AllIcons.Actions.Checked) {
        override fun actionPerformed(e: AnActionEvent) {
          val selected = accountsList.selectedValue
          if (selected == accountsModel.defaultAccount) return
          if (selected != null) accountsModel.defaultAccount = selected
        }

        override fun updateButton(e: AnActionEvent) {
          isEnabled = isEnabled && accountsModel.defaultAccount != accountsList.selectedValue
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
      })
    }

    return toolbar.createPanel()
  }

  private fun <A : Account> loadAccountsDetails(accountsList: JBList<A>,
                                                detailsLoader: AccountsDetailsLoader<A, *>,
                                                resultsMap: MutableMap<A, CompletableFuture<AccountsDetailsLoader.Result<*>>>) {

    val listModel = accountsList.model
    listModel.addListDataListener(object : ListDataListener {
      private var loadingCount by observable(0) { _, _, newValue ->
        accountsList.setPaintBusy(newValue != 0)
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
        if (startIdx < 0 || endIdx < 0) return

        for (i in startIdx..endIdx) {
          val account = listModel.getElementAt(i)
          resultsMap[account]?.cancel(true)
          loadingCount++
          val detailsLoadingResult = detailsLoader.loadDetailsAsync(account).asCompletableFuture()
          detailsLoadingResult.handleOnEdt(ModalityState.any()) { _, _ ->
            loadingCount--
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
}
