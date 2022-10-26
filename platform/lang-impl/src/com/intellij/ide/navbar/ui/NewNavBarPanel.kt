// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.vm.NavBarPopupVm
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.navbar.vm.PopupResult
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.HintHint
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class NewNavBarPanel(
  cs: CoroutineScope,
  private val vm: NavBarVm,
  private val myProject: Project,
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

  private val myItemComponents = arrayListOf<NavBarItemComponent>()

  var onSizeChange: Runnable? = null

  init {
    EDT.assertIsEdt()

    cs.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      vm.items.collect {
        rebuild(it)
      }
    }

    cs.launch(Dispatchers.EDT) {
      vm.popup.collect { (item, popupVM) ->
        showPopup(item, popupVM)
      }
    }
  }

  private fun rebuild(items: List<NavBarVmItem>) {
    EDT.assertIsEdt()
    removeAll()
    myItemComponents.clear()
    items.forEachIndexed { i, item ->
      val isLast = i == items.size - 1

      val isIconNeeded = Registry.`is`("navBar.show.icons")
                         || isLast
                         || item.presentation.hasContainingFile

      val itemComponent = NavBarItemComponent(item, myProject, isIconNeeded, !isLast)

      itemComponent.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.isConsumed) {
            return
          }
          if (e.button == MouseEvent.BUTTON1) {
            e.consume()
            if (e.clickCount == 1) {
              vm.selectItem(item)
            }
            else {
              vm.activateItem(item)
            }
          }
        }
      })

      add(itemComponent)
      myItemComponents.add(itemComponent)
    }

    revalidate()
    repaint()

    SwingUtilities.invokeLater {
      myItemComponents.lastOrNull()?.let {
        scrollRectToVisible(it.bounds)
      }
    }

    onSizeChange?.run()
  }

  private fun showPopup(item: NavBarVmItem, vm: NavBarPopupVm) {
    val itemComponent = myItemComponents.lastOrNull {
      it.item === item
    }
    if (itemComponent == null) {
      vm.popupResult(PopupResult.PopupResultCancel)
      return
    }
    scrollRectToVisible(itemComponent.bounds)
    val popupHint = NavigationBarPopup(vm, myProject)
    val point = getItemPopupLocation(itemComponent, popupHint)
    popupHint.show(this, point.x, point.y, this, HintHint(this, point))
  }

  private fun getItemPopupLocation(itemComponent: Component, popupHint: NavigationBarPopup): Point {
    val relativeY = if (ExperimentalUI.isNewUI() && UISettings.getInstance().showNavigationBarInBottom) {
      -popupHint.component.preferredSize.height
    }
    else {
      itemComponent.height
    }
    val relativePoint = RelativePoint(itemComponent, Point(0, relativeY))
    return relativePoint.getPoint(this)
  }
}
