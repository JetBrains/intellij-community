// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.impl.PsiNavBarItem
import com.intellij.ide.navbar.ide.ItemClickEvent
import com.intellij.ide.navbar.ide.ItemSelectType.NAVIGATE
import com.intellij.ide.navbar.ide.ItemSelectType.OPEN_POPUP
import com.intellij.ide.navbar.ide.UiNavBarItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel


internal class NavBarPanel(
  cs: CoroutineScope,
  private val itemsFlow: Flow<List<UiNavBarItem>>,
  private val itemClickEvents: MutableSharedFlow<ItemClickEvent>,
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

  private val myItemComponents = arrayListOf<NavBarItemComponent>()

  init {
    cs.launch(Dispatchers.EDT) {
      itemsFlow.collect {
        render(it)
      }
    }
  }

  fun getItemPopupLocation(i: Int): Point {
    val itemComponent = myItemComponents.getOrNull(i) ?: return Point(0, 0)
    val relativeX = 0
    val relativeY = itemComponent.height
    val relativePoint = RelativePoint(itemComponent, Point(relativeX, relativeY))
    return relativePoint.getPoint(this)
  }

  fun scrollTo(index: Int) {
    myItemComponents.getOrNull(index)?.let {
      scrollRectToVisible(it.bounds)
    }
  }

  private suspend fun render(myItems: List<UiNavBarItem>) {
    check(myItems.isNotEmpty())
    removeAll()
    myItemComponents.clear()
    myItems.forEachIndexed { i, item ->
      val isLast = i == myItems.size - 1

      val isIconNeeded = Registry.`is`("navBar.show.icons")
                         || isLast
                         || item.hasContainingFile()

      val itemComponent = NavBarItemComponent(item.presentation, isIconNeeded, !isLast)

      itemComponent.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (!e.isConsumed) {
            e.consume()
            if (e.clickCount == 1) {
              itemClickEvents.tryEmit(ItemClickEvent(OPEN_POPUP, i, item))
            }
            else if (e.button == MouseEvent.BUTTON1) {
              itemClickEvents.tryEmit(ItemClickEvent(NAVIGATE, i, item))
            }
          }
        }
      })

      add(itemComponent)
      myItemComponents.add(itemComponent)
    }

    // TODO: sort out this crap below, need to be sure child elements has proper size
    revalidate()
    doLayout()
    repaint()
  }

  override fun paint(g: Graphics?) {
    super.paint(g)
    myItemComponents.lastOrNull()?.let {
      scrollRectToVisible(it.bounds)
    }
  }

}


private suspend fun UiNavBarItem.hasContainingFile(): Boolean =
  withContext(Dispatchers.Default) {
    readAction {
      val psiItem = pointer.dereference() as? PsiNavBarItem
      psiItem?.data?.containingFile != null
    }
  }
