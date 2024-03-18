// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.messages.CollaborationToolsBundle.message
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.asObservableIn
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.findIndex
import com.intellij.collaboration.ui.util.JListHoveredRowMaterialiser
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.progress.runBlockingModalWithRawProgressReporter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
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
      SimpleAccountsListCellRenderer({ (accountsModel is AccountsListModel.WithDefault<*, *>) && it == accountsModel.defaultAccount },
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
      runBlockingModalWithRawProgressReporter(ModalTaskOwner.component(component), message("accounts.saving.credentials")) {
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
      appendText(message("accounts.none.added"))
      appendSecondaryText(message("accounts.add.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
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
      // TODO: move to controller???
      .setRemoveAction { accountsList.selectedValue.let { (accountsModel as? MutableAccountsListModel)?.remove(it) } }
      .setRemoveActionUpdater { accountsModel is MutableAccountsListModel && accountsList.selectedValue != null }

    if (accountsModel is AccountsListModel.WithDefault) {
      toolbar.addExtraAction(object : DumbAwareAction(message("accounts.set.default"),
                                                      null, AllIcons.Actions.Checked) {
        override fun actionPerformed(e: AnActionEvent) {
          val selected = accountsList.selectedValue
          if (selected == accountsModel.defaultAccount) return
          if (selected != null) accountsModel.defaultAccount = selected
        }

        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = accountsModel.defaultAccount != accountsList.selectedValue
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT
      })
    }

    return toolbar.createPanel()
  }

  companion object {
    /**
     * Adds a warning to a panel that tells the user that passwords and other credentials
     * are currently not persisted to disk.
     *
     * Note: an observable property is created under the given coroutine scope that will
     * live on for as long as the scope is live. This means the scope needs to be cancelled
     * manually or through a disposing scope.
     */
    @ApiStatus.Experimental
    fun addWarningForPersistentCredentials(cs: CoroutineScope,
                                           canPersistCredentials: Flow<Boolean>,
                                           panel: (Panel.() -> Unit) -> Panel,
                                           solution: ((DataContext) -> Unit)? = null) {
      panel {
        row {
          cell(HorizontalListPanel(4).apply {
            val warning = message(if (solution != null) "accounts.error.password-not-saved.colon" else "accounts.error.password-not-saved")
            add(JLabel(warning).apply {
              foreground = NamedColorUtil.getErrorForeground()
            })

            if (solution != null) {
              add(ActionLink(message("accounts.error.password-not-saved.link")).apply {
                addActionListener {
                  if (it.source != this) return@addActionListener
                  solution(DataManager.getInstance().getDataContext(this))
                }
              })
            }
          }).align(AlignX.RIGHT)
            .visibleIf(canPersistCredentials.map { !it }
                         .stateIn(cs, SharingStarted.Lazily, false)
                         .asObservableIn(cs))
        }
      }.align(AlignX.FILL)
    }

    /**
     * Adds a warning to a panel that tells the user that password safe settings are
     * currently not set to persistent storage, meaning no passwords or tokens are
     * persisted to storage.
     *
     * This specific function also adds a link to the settings page to solve it.
     */
    @ApiStatus.Experimental
    fun addWarningForMemoryOnlyPasswordSafe(cs: CoroutineScope,
                                            canPersistCredentials: Flow<Boolean>,
                                            panel: (Panel.() -> Unit) -> Panel) {
      addWarningForPersistentCredentials(cs, canPersistCredentials, panel) {
        val settings = Settings.KEY.getData(it)
        settings?.select(settings.find("application.passwordSafe"))
      }
    }
  }
}
