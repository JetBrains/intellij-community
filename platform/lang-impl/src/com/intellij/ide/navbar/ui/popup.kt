// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.actions.OpenInRightSplitAction
import com.intellij.ide.navbar.ide.NavBarVmItem
import com.intellij.ide.navbar.impl.PsiNavBarItem
import com.intellij.ide.navbar.vm.NavBarPopupItem
import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.ide.navigationToolbar.NavBarListWrapper
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.LightweightHint
import com.intellij.ui.PopupHandler
import com.intellij.ui.popup.HintUpdateSupply
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.util.SlowOperations
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JList

internal fun createNavBarPopup(list: JList<NavBarPopupItem>): LightweightHint {
  // TODO implement async hint update supply
  HintUpdateSupply.installHintUpdateSupply(list) { item ->
    SlowOperations.allowSlowOperations(ThrowableComputable {
      ((item as? NavBarVmItem)?.pointer?.dereference() as? PsiNavBarItem)?.data
    })
  }
  val popupComponent = list.withSpeedSearch()
  val popup = object : LightweightHint(popupComponent) {
    override fun onPopupCancel() {
      HintUpdateSupply.hideHint(list)
    }
  }
  popup.setFocusRequestor(popupComponent)
  popup.setForceShowAsPopup(true)
  return popup
}

internal fun navBarPopupList(
  vm: NavBarPopupVm,
  contextComponent: Component,
  floating: Boolean,
): JList<NavBarPopupItem> {
  val list = ContextJBList<NavBarPopupItem>(contextComponent)
  list.model = CollectionListModel(vm.items)
  list.cellRenderer = NavBarPopupListCellRenderer(floating)
  list.border = JBUI.Borders.empty(5)
  list.background = JBUI.CurrentTheme.Popup.BACKGROUND
  list.addListSelectionListener {
    vm.itemsSelected(list.selectedValuesList)
  }
  PopupHandler.installPopupMenu(list, NavBarContextMenuActionGroup(), ActionPlaces.NAVIGATION_BAR_POPUP)
  list.addMouseListener(object : MouseAdapter() {

    override fun mousePressed(e: MouseEvent) {
      if (!SystemInfo.isWindows) {
        click(e)
      }
    }

    override fun mouseReleased(e: MouseEvent) {
      if (SystemInfo.isWindows) {
        click(e)
      }
    }

    private fun click(e: MouseEvent) {
      if (!e.isPopupTrigger && e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
        vm.complete()
      }
    }
  })
  return list
}

private fun JList<NavBarPopupItem>.withSpeedSearch(): JComponent {
  val wrapper = NavBarListWrapper(this)
  val component = ListWithFilter.wrap(this, wrapper) { item ->
    item.presentation.popupText ?: item.presentation.text
  } as ListWithFilter<*>
  wrapper.updateViewportPreferredSizeIfNeeded() // this fixes IDEA-301848 for some reason
  component.setAutoPackHeight(!UISettings.getInstance().showNavigationBarInBottom)
  OpenInRightSplitAction.overrideDoubleClickWithOneClick(component)
  return component
}
