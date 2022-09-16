// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.ide.navbar.ide.ItemClickEvent
import com.intellij.ide.navbar.ide.ItemSelectType.NAVIGATE
import com.intellij.ide.navbar.ide.ItemSelectType.OPEN_POPUP
import com.intellij.ide.navbar.ide.UiNavBarItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.SwingUtilities


internal class NavBarPanel(
  private val itemClickEvents: MutableSharedFlow<ItemClickEvent>,
  private val myItems: StateFlow<List<UiNavBarItem>>,
  cs: CoroutineScope
) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

  private val myItemComponents = arrayListOf<NavBarItemComponent>()

  var onSizeChange: Consumer<Dimension>? = null

  init {
    EDT.assertIsEdt()

    cs.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      myItems.collect {
        rebuild(it)
      }
    }

  }

  private fun rebuild(items: List<UiNavBarItem>) {
    EDT.assertIsEdt()
    removeAll()
    myItemComponents.clear()
    items.forEachIndexed { i, item ->
      val isLast = i == items.size - 1

      val isIconNeeded = Registry.`is`("navBar.show.icons")
                         || isLast
                         || item.presentation.hasContainingFile

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

    revalidate()
    repaint()

    SwingUtilities.invokeLater {
      myItemComponents.lastOrNull()?.let {
        scrollRectToVisible(it.bounds)
      }
    }

    onSizeChange?.accept(preferredSize)

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

}
