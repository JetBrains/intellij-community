// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.navbar.vm.PopupResult.*
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.LightweightHint
import com.intellij.ui.ListActions
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.popup.HintUpdateSupply
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.event.*
import javax.swing.*

private fun registerListActions(list: JList<NavBarVmItem>, popup: LightweightHint, vm: NavBarPopupVm) {

  list.actionMap.put(ListActions.Left.ID, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      vm.popupResult(PopupResultLeft)
      popup.hide(true)
    }
  })

  list.actionMap.put(ListActions.Right.ID, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      vm.popupResult(PopupResultRight)
      popup.hide(true)
    }
  })

  list.registerKeyboardAction(object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      vm.popupResult(PopupResultSelect(list.selectedValue))
      popup.hide(true)
    }
  }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)

  list.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      vm.popupResult(PopupResultSelect(list.selectedValue))
      popup.hide(true)
    }
  })

}

internal class NavigationBarPopup(private val vm: NavBarPopupVm) : LightweightHint(createPopupContents(vm)) {

  init {
    setFocusRequestor(component)
    setForceShowAsPopup(true)
    registerListActions((component as ListWithFilter<NavBarVmItem>).list, this, vm)
  }

  override fun onPopupCancel() {
    vm.popupResult(PopupResultCancel)
  }

  companion object {
    private fun createPopupContents(vm: NavBarPopupVm): JComponent {
      val list = JBList<NavBarVmItem>()
      list.model = CollectionListModel(vm.items)
      list.border = JBUI.Borders.empty(5)
      HintUpdateSupply.installSimpleHintUpdateSupply(list)

      list.installCellRenderer { item -> NavigationBarPopupItemComponent(item.presentation) }

      val selectedChild = vm.selectedChild
      if (selectedChild != null) {
        list.setSelectedValue(selectedChild, /* shouldScroll = */ true)
      }
      else {
        list.selectedIndex = 0
      }

      return ListWithFilter.wrap(list, NavBarListWrapper(list)) { item ->
        item.presentation.popupText ?: item.presentation.text
      }
    }
  }

}


private class NavBarListWrapper<T>(private val myList: JList<T>) : JBScrollPane(myList), DataProvider {

  init {
    myList.addMouseMotionListener(object : MouseMotionAdapter() {
      var myIsEngaged = false
      override fun mouseMoved(e: MouseEvent) {
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e)) {
          val point = e.point
          val index = myList.locationToIndex(point)
          myList.setSelectedIndex(index)
        }
        else {
          myIsEngaged = true
        }
      }
    })
    ScrollingUtil.installActions(myList)
    val modelSize = myList.model.size
    border = BorderFactory.createEmptyBorder()
    if (modelSize in 1..MAX_SIZE) {
      myList.visibleRowCount = 0
      getViewport().preferredSize = myList.preferredSize
    }
    else {
      myList.setVisibleRowCount(MAX_SIZE)
    }
  }

  override fun getData(@NonNls dataId: String): Any? = when {
    PlatformCoreDataKeys.SELECTED_ITEM.`is`(dataId) -> myList.selectedValue
    PlatformCoreDataKeys.SELECTED_ITEMS.`is`(dataId) -> myList.selectedValues
    else -> null
  }

  override fun requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
      IdeFocusManager.getGlobalInstance().requestFocus(myList, true)
    }
  }

  @Synchronized
  override fun addMouseListener(l: MouseListener) {
    myList.addMouseListener(l)
  }

  companion object {
    private const val MAX_SIZE = 20
  }
}
