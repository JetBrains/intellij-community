// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindIn
import com.intellij.collaboration.ui.util.name
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.popup.list.ComboBoxPopup
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.AccountMenuItemRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Cursor
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class AccountSelectorComponentFactory<A : Account>(
  private val accountsState: StateFlow<Collection<A>>,
  private val selectionState: MutableStateFlow<A?>,
) {

  fun create(
    scope: CoroutineScope,
    defaultAccountHolder: DefaultAccountHolder<A>?,
    avatarIconsProvider: IconsProvider<A>,
    avatarSize: Int,
    popupAvatarSize: Int,
    emptyStateTooltip: @Nls String,
    actions: StateFlow<List<Action>> = MutableStateFlow(emptyList()),
  ): JComponent {
    val comboModel = ComboBoxWithActionsModel<A>().apply {
      bindIn(scope, accountsState, selectionState, actions, Comparator.comparing { it.name })

      val defaultAccount = defaultAccountHolder?.account
      if (selectedItem == null && defaultAccount != null) {
        selectedItem = ComboBoxWithActionsModel.Item.Wrapper(defaultAccount)
      }

      if (size > 0 && selectedItem == null) {
        for (i in 0 until size) {
          val item = getElementAt(i)
          if (item is ComboBoxWithActionsModel.Item.Wrapper) {
            selectedItem = item
            break
          }
        }
      }
    }

    val label = JLabel().apply {
      isOpaque = false
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isFocusable = true
      border = SimpleFocusBorder()
    }
    Controller(comboModel, label, avatarIconsProvider, avatarSize, popupAvatarSize, emptyStateTooltip)
    return label
  }

  private class Controller<A : Account>(
    private val accountsModel: ComboBoxWithActionsModel<A>,
    private val label: JLabel,
    private val avatarIconsProvider: IconsProvider<A>,
    private val avatarSize: Int,
    private val popupAvatarSize: Int,
    private val emptyStateTooltip: @Nls String,
  )
    : ComboBoxPopup.Context<ComboBoxWithActionsModel.Item<A>> {

    private var popup: ComboBoxPopup<*>? = null

    init {
      model.addListDataListener(object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent) = updateLabel()
        override fun intervalAdded(e: ListDataEvent?) = updateLabel()
        override fun intervalRemoved(e: ListDataEvent?) = updateLabel()
      })
      label.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) = showPopup()
      })
      label.registerPopupOnKeyboardShortcut(KeyEvent.VK_ENTER)
      label.registerPopupOnKeyboardShortcut(KeyEvent.VK_SPACE)
      updateLabel()
    }

    private fun updateLabel() {
      val selectedAccount = accountsModel.selectedItem?.wrappee
      with(label) {
        icon = avatarIconsProvider.getIcon(selectedAccount, avatarSize)
        toolTipText = selectedAccount?.name ?: emptyStateTooltip
      }
    }

    private fun showPopup() {
      if (!label.isEnabled) return
      popup = object : ComboBoxPopup<ComboBoxWithActionsModel.Item<A>>(this, accountsModel.selectedItem, {
        accountsModel.setSelectedItem(it)
      }) {
        //otherwise component borders are overridden
        override fun getListElementRenderer() = renderer
      }.apply {
        //TODO: remove speedsearch
        addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            popup = null
          }
        })
        showUnderneathOf(label)
      }
    }

    private fun JComponent.registerPopupOnKeyboardShortcut(keyCode: Int) {
      registerKeyboardAction({ showPopup() }, KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_FOCUSED)
    }

    override fun getProject(): Project? = null

    override fun getModel(): ListModel<ComboBoxWithActionsModel.Item<A>> = accountsModel

    override fun getRenderer(): ListCellRenderer<ComboBoxWithActionsModel.Item<A>> = PopupItemRenderer()

    private inner class PopupItemRenderer : ListCellRenderer<ComboBoxWithActionsModel.Item<A>> {

      private val delegateRenderer = AccountMenuItemRenderer()

      override fun getListCellRendererComponent(
        list: JList<out ComboBoxWithActionsModel.Item<A>>?,
        value: ComboBoxWithActionsModel.Item<A>,
        index: Int,
        selected: Boolean,
        focused: Boolean,
      ): Component {
        val item = when (value) {
          is ComboBoxWithActionsModel.Item.Wrapper<A> ->
            value.wrappee.let { account ->
              val icon = avatarIconsProvider.getIcon(account, popupAvatarSize)
              val serverAddress = (account as? ServerAccount)?.server?.toString().orEmpty()
              AccountMenuItem.Account(account.name, serverAddress, icon)
            }
          is ComboBoxWithActionsModel.Item.Action<A> ->
            value.action.let {
              AccountMenuItem.Action(it.name.orEmpty(), {}, showSeparatorAbove = value.needSeparatorAbove)
            }
        }
        return delegateRenderer.getListCellRendererComponent(null, item, index, selected, focused)
      }
    }
  }
}