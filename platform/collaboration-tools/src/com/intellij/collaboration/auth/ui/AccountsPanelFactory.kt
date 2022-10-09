// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.findIndex
import com.intellij.collaboration.ui.util.JListHoveredRowMaterialiser
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import javax.swing.*

class AccountsPanelFactory<A : Account, Cred>
private constructor(private val accountManager: AccountManager<A, Cred>,
                    private val defaultAccountHolder: DefaultAccountHolder<A>?,
                    private val accountsModel: AccountsListModel<A, Cred>,
                    private val scope: CoroutineScope) {

  constructor(scope: CoroutineScope,
              accountManager: AccountManager<A, Cred>,
              defaultAccountHolder: DefaultAccountHolder<A>,
              accountsModel: AccountsListModel.WithDefault<A, Cred>) : this(accountManager, defaultAccountHolder, accountsModel, scope)

  constructor(scope: CoroutineScope,
              accountManager: AccountManager<A, Cred>,
              accountsModel: AccountsListModel<A, Cred>) : this(accountManager, null, accountsModel, scope)

  fun accountsPanelCell(row: Row,
                        detailsProvider: LoadingAccountsDetailsProvider<A, *>,
                        actionsController: AccountsPanelActionsController<A>): Cell<JComponent> {

    val accountsList = createList(actionsController, detailsProvider) {
      SimpleAccountsListCellRenderer({ (accountManager is AccountsListModel.WithDefault<*, *>) && it == accountManager.defaultAccount },
                                     detailsProvider, actionsController)
    }

    val component = wrapWithToolbar(accountsList, actionsController)

    return row.cell(component)
      .onIsModified(::isModified)
      .onReset(::reset)
      .onApply { apply(component) }
  }

  private fun isModified(): Boolean {
    val defaultModified = if (defaultAccountHolder != null && accountsModel is AccountsListModel.WithDefault) {
      accountsModel.defaultAccount != defaultAccountHolder.account
    }
    else false

    return accountsModel.newCredentials.isNotEmpty()
           || accountsModel.accounts != accountManager.accountsState.value
           || defaultModified
  }

  private fun reset() {
    accountsModel.accounts = accountManager.accountsState.value
    if (defaultAccountHolder != null && accountsModel is AccountsListModel.WithDefault) {
      accountsModel.defaultAccount = defaultAccountHolder.account
    }
    accountsModel.clearNewCredentials()
  }

  private fun apply(component: JComponent) {
    try {
      val newTokensMap = mutableMapOf<A, Cred?>()
      newTokensMap.putAll(accountsModel.newCredentials)
      for (account in accountsModel.accounts) {
        newTokensMap.putIfAbsent(account, null)
      }
      runBlockingModal(ModalTaskOwner.component(component), CollaborationToolsBundle.message("accounts.saving.credentials")) {
        accountManager.updateAccounts(newTokensMap)
      }
      accountsModel.clearNewCredentials()

      if (defaultAccountHolder != null && accountsModel is AccountsListModel.WithDefault) {
        val defaultAccount = accountsModel.defaultAccount
        defaultAccountHolder.account = defaultAccount
      }
    }
    catch (_: Exception) {
    }
  }

  private fun <R> createList(actionsController: AccountsPanelActionsController<A>,
                             detailsLoadingVm: LoadingAccountsDetailsProvider<A, *>,
                             listCellRendererFactory: () -> R)
    : JBList<A> where R : ListCellRenderer<A>, R : JComponent {

    val accountsList = JBList(accountsModel.accountsListModel).apply {
      val renderer = listCellRendererFactory()
      cellRenderer = renderer
      UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))

      selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    val rowMaterialiser = JListHoveredRowMaterialiser.install(accountsList, listCellRendererFactory())

    scope.launch {
      detailsLoadingVm.loadingState.collect {
        accountsList.setPaintBusy(it)
      }
    }

    scope.launch {
      detailsLoadingVm.loadingCompletionFlow.collect {
        repaint(accountsList, it)
        rowMaterialiser.update()
      }
    }

    accountsList.addListSelectionListener { accountsModel.selectedAccount = accountsList.selectedValue }

    accountsList.emptyText.apply {
      appendText(CollaborationToolsBundle.message("accounts.none.added"))
      appendSecondaryText(CollaborationToolsBundle.message("accounts.add.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        val event = it.source
        val relativePoint = if (event is MouseEvent) RelativePoint(event) else null
        actionsController.addAccount(accountsList, relativePoint)
      }
      appendSecondaryText(" (${KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNew())})", StatusText.DEFAULT_ATTRIBUTES, null)
    }
    return accountsList
  }

  private fun repaint(list: JList<A>, account: A): Boolean {
    val idx = list.model.findIndex(account).takeIf { it >= 0 } ?: return true
    val cellBounds = list.getCellBounds(idx, idx)
    list.repaint(cellBounds)
    return false
  }

  private fun wrapWithToolbar(accountsList: JBList<A>, actionsController: AccountsPanelActionsController<A>): JPanel {
    val addIcon: Icon = if (actionsController.isAddActionWithPopup) LayeredIcon.ADD_WITH_DROPDOWN else AllIcons.General.Add

    val toolbar = ToolbarDecorator.createDecorator(accountsList)
      .disableUpDownActions()
      .setAddAction { actionsController.addAccount(accountsList, it.preferredPopupPoint) }
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
}
