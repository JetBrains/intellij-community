// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.CopyPasteDelegator
import com.intellij.ide.CopyPasteSupport
import com.intellij.ide.IdeBundle
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.actions.NavBarActionHandler.NAV_BAR_ACTION_HANDLER
import com.intellij.ide.navbar.actions.NavBarActionHandlerImpl
import com.intellij.ide.navbar.actions.extensionData
import com.intellij.ide.navbar.actions.getBgData
import com.intellij.ide.navbar.ide.LOG
import com.intellij.ide.navbar.ui.NavBarItemComponent.Companion.isItemComponentFocusable
import com.intellij.ide.navbar.vm.NavBarItemVm
import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.ide.ui.UISettings
import com.intellij.internal.statistic.service.fus.collectors.NavBarShowPopup
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.BGT_DATA_PROVIDER
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.actionSystem.PlatformDataKeys.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.ui.EDT
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.border.LineBorder

internal class NewNavBarPanel(
  cs: CoroutineScope,
  private val vm: NavBarVm,
  val project: Project,
  val isFloating: Boolean,
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)),
    DataProvider {

  private val myItemComponents: ArrayList<NavBarItemComponent> = ArrayList()

  var onSizeChange: Runnable? = null
    set(value) {
      EDT.assertIsEdt()
      field = value
    }

  init {
    EDT.assertIsEdt()
    isOpaque = false
    if (!ExperimentalUI.isNewUI() && StartupUiUtil.isUnderDarcula() && isFloating) {
      border = LineBorder(Gray._120, 1)
    }
    AccessibleContextUtil.setName(this, IdeBundle.message("navigation.bar"))

    if (!isFloating) {
      addFocusListener(NavBarDialogFocusListener(this))
    }
    addFocusListener(object : FocusListener {
      override fun focusGained(e: FocusEvent?) = myItemComponents.forEach(NavBarItemComponent::update)
      override fun focusLost(e: FocusEvent?) = myItemComponents.forEach(NavBarItemComponent::update)
    })
    cs.launch {
      handleItems()
    }
  }

  private suspend fun handleItems() {
    vm.items.collectLatest { items ->
      coroutineScope {
        withContext(Dispatchers.EDT) {
          rebuild(this@coroutineScope, items)
        }
        handleSelection()
      }
    }
  }

  private suspend fun handleSelection() {
    vm.selectedIndex.collectLatest { index ->
      if (index < 0) {
        return@collectLatest
      }
      withContext(Dispatchers.EDT) {
        val itemComponent = myItemComponents[index]
        scrollRectToVisible(itemComponent.bounds)
        itemComponent.focusItem()
        if (!ExperimentalUI.isNewUI()) {
          // Components update themselves on selection change.
          // In total 2 components ate updated: the one which became selected, and the one which lost the selection.
          // In the old chevron needs to know whether the next component is selected
          // regardless of selection moving to the left or right.
          // I don't really want to expose and maintain [NavBarItemVm#isNextSelected] as StateFlow,
          // so in the old UI all components are updated on each selection change.
          myItemComponents.forEach(NavBarItemComponent::update)
        }
      }
      handlePopup(index)
    }
  }

  private suspend fun handlePopup(index: Int) {
    vm.popup.collectLatest { popup ->
      if (popup == null) {
        return@collectLatest
      }
      withContext(Dispatchers.EDT) {
        showPopup(this@withContext, index, popup)
      }
    }
  }

  private suspend fun rebuild(cs: CoroutineScope, items: List<NavBarItemVm>) {
    EDT.assertIsEdt()
    removeAll()
    myItemComponents.clear()

    for (item in items) {
      val itemComponent = NavBarItemComponent(cs, item, this)
      add(itemComponent)
      myItemComponents.add(itemComponent)
    }

    revalidate()
    repaint()

    onSizeChange?.run()
    while (!isValid) {
      yield()
    }
    myItemComponents.lastOrNull()?.let {
      scrollRectToVisible(it.bounds)
    }
  }

  private var popupList: WeakReference<JList<*>>? = null
    get() {
      EDT.assertIsEdt()
      return field
    }
    set(value) {
      EDT.assertIsEdt()
      field = value
    }

  private fun showPopup(cs: CoroutineScope, itemComponentIndex: Int, vm: NavBarPopupVm) {
    NavBarShowPopup.log(project)
    val itemComponent = myItemComponents[itemComponentIndex]
    val list = navBarPopupList(vm, this, isFloating).also {
      AccessibleContextUtil.setName(it, itemComponent.text)
    }.also {
      popupList = WeakReference(it)
    }
    if (!isShowing) {
      LOG.warn("Navigation bar panel is now showing => cannot show child popup")
      return
    }
    val popup = createNavBarPopup(list)
    popup.addHintListener {
      vm.cancel() // cancel vm when popup is cancelled
    }
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      catch (e: CancellationException) {
        popupList = null
        popup.hide() // cancel the popup when coroutine is cancelled
        throw e
      }
    }
    val offsetX = navBarPopupOffset(itemComponentIndex == 0)
    val point = getItemPopupLocation(itemComponent, popup)
    popup.show(this, point.x - offsetX, point.y, this, HintHint(this, point))
    val selectedItemIndex = vm.initialSelectedItemIndex
    if (selectedItemIndex in 0 until list.model.size) {
      ScrollingUtil.selectItem(list, selectedItemIndex)
    }
  }

  private fun getItemPopupLocation(itemComponent: Component, popupHint: LightweightHint): Point {
    val settings = UISettings.getInstance()
    val relativeY = if (ExperimentalUI.isNewUI() && settings.showNavigationBarInBottom && settings.showStatusBar) {
      -popupHint.component.preferredSize.height
    }
    else {
      itemComponent.height
    }
    val relativePoint = RelativePoint(itemComponent, Point(0, relativeY))
    return relativePoint.getPoint(this)
  }

  fun isItemFocused(): Boolean {
    return when {
      vm.popup.value != null -> false
      isItemComponentFocusable() -> UIUtil.isFocusAncestor(this)
      else -> hasFocus()
    }
  }

  override fun getData(dataId: String): Any? = when (dataId) {
    NAV_BAR_ACTION_HANDLER.name -> object : NavBarActionHandlerImpl(vm) {
      override fun isNodePopupSpeedSearchActive(): Boolean {
        val list = popupList?.get()
        return list != null && SpeedSearchSupply.getSupply(list) != null
      }
    }
    CONTEXT_COMPONENT.name -> this
    PROJECT.name -> project
    CUT_PROVIDER.name -> extensionData(dataId) ?: getCopyPasteDelegator(this).cutProvider
    COPY_PROVIDER.name -> extensionData(dataId) ?: getCopyPasteDelegator(this).copyProvider
    PASTE_PROVIDER.name -> extensionData(dataId) ?: getCopyPasteDelegator(this).pasteProvider
    BGT_DATA_PROVIDER.name -> {
      val selection: List<Pointer<out NavBarItem>> = vm.selection()
      DataProvider {
        getBgData(project, selection, it)
      }
    }
    else -> null
  }

  private fun getCopyPasteDelegator(source: JComponent): CopyPasteSupport {
    val key = "NavBarPanel.copyPasteDelegator"
    val result = source.getClientProperty(key)
    if (result is CopyPasteSupport) {
      return result
    }
    else {
      return CopyPasteDelegator(project, source).also {
        source.putClientProperty(key, it)
      }
    }
  }
}
