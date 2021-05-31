// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.AccountsListener
import com.intellij.collaboration.auth.PersistentDefaultAccountHolder
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.JListHoveredRowMaterialiser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

object AccountsPanelFactory {

  fun <A : Account, Cred, R> create(model: AccountsListModel<A, Cred>, listCellRendererFactory: () -> R): JComponent
    where R : ListCellRenderer<A>, R : JComponent {

    val accountsListModel = model.accountsListModel
    val accountsList = JBList(accountsListModel).apply {
      val decoratorRenderer = listCellRendererFactory()
      cellRenderer = decoratorRenderer
      JListHoveredRowMaterialiser.install(this, listCellRendererFactory())
      UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(decoratorRenderer))

      selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    model.busyStateModel.addInvokeListener {
      accountsList.setPaintBusy(it)
    }

    accountsList.emptyText.apply {
      appendText(CollaborationToolsBundle.message("accounts.none.added"))
      appendSecondaryText(CollaborationToolsBundle.message("accounts.add.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        //FIXME: proper point for a popup
        model.addAccount(accountsList)
      }
      appendSecondaryText(" (${KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getNew())})", StatusText.DEFAULT_ATTRIBUTES, null)
    }

    model.busyStateModel.addListener {
      accountsList.setPaintBusy(it)
    }

    return ToolbarDecorator.createDecorator(accountsList)
      .disableUpDownActions()
      .setAddAction { model.addAccount(accountsList, it.preferredPopupPoint) }
      .setAddIcon(LayeredIcon.ADD_WITH_DROPDOWN)
      .addExtraAction(object : ToolbarDecorator.ElementActionButton(CollaborationToolsBundle.message("accounts.set.default"),
                                                                    AllIcons.Actions.Checked) {
        override fun actionPerformed(e: AnActionEvent) {
          val selected = accountsList.selectedValue
          if (selected == model.defaultAccount) return
          if (selected != null) model.defaultAccount = selected
        }

        override fun updateButton(e: AnActionEvent) {
          isEnabled = isEnabled && model.defaultAccount != accountsList.selectedValue
        }
      })
      .createPanel()
  }

  fun <A : Account, Cred> Row.accountsPanel(accountManager: AccountManager<A, Cred>,
                                            defaultAccountHolder: PersistentDefaultAccountHolder<A>,
                                            accountsModel: AccountsListModel<A, Cred>,
                                            detailsProvider: AccountsDetailsProvider<A, *>,
                                            disposable: Disposable,
                                            defaultAvatarIcon: Icon = EmptyIcon.ICON_16): CellBuilder<JComponent> {

    accountsModel.addCredentialsChangeListener(detailsProvider::reset)
    detailsProvider.loadingStateModel.addInvokeListener {
      accountsModel.busyStateModel.value = it
    }

    fun isModified() = accountsModel.newCredentials.isNotEmpty()
                       || accountsModel.accounts != accountManager.accounts
                       || accountsModel.defaultAccount != defaultAccountHolder.account

    fun reset() {
      accountsModel.accounts = accountManager.accounts
      accountsModel.defaultAccount = defaultAccountHolder.account
      accountsModel.clearNewCredentials()
      detailsProvider.resetAll()
    }

    fun apply() {
      val newTokensMap = mutableMapOf<A, Cred?>()
      newTokensMap.putAll(accountsModel.newCredentials)
      for (account in accountsModel.accounts) {
        newTokensMap.putIfAbsent(account, null)
      }
      val defaultAccount = accountsModel.defaultAccount
      accountManager.updateAccounts(newTokensMap)
      defaultAccountHolder.account = defaultAccount
      accountsModel.clearNewCredentials()
    }

    accountManager.addListener(disposable, object : AccountsListener<A> {
      override fun onAccountCredentialsChanged(account: A) {
        if (!isModified()) reset()
      }
    })

    return create(accountsModel) {
      SimpleAccountsListCellRenderer(accountsModel, detailsProvider, defaultAvatarIcon)
    }(grow, push)
      .onIsModified(::isModified)
      .onReset(::reset)
      .onApply(::apply)
  }
}