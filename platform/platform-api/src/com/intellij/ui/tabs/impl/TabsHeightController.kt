// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.ui.JBUI
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

object TabsHeightController {
  private val watchedComponents = mutableListOf<JComponent>()
  private val listeners = mutableListOf<(Int) -> Unit>()
  private var toolWindowHeight = TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding())

  private fun recalcHeight() {
    val height = (watchedComponents.map { it.height }.max() ?: 0).coerceAtLeast(TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding()))
    if (height != toolWindowHeight) {
      toolWindowHeight = height
      listeners.forEach { it(height) }
    }
  }

  @JvmStatic
  fun registerActive(comp: JComponent, parentDisposable: Disposable) {
    val listener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        recalcHeight()
      }
    }

    watchedComponents.add(comp)
    recalcHeight()
    comp.addComponentListener(listener)

    Disposer.register(parentDisposable, Disposable {
      watchedComponents.remove(comp)
      recalcHeight()
      comp.removeComponentListener(listener)
    })
  }

  @JvmStatic
  fun addListener(update: (Int) -> Unit, parentDisposable: Disposable) {
    listeners.add(update)
    Disposer.register(parentDisposable, Disposable { listeners.remove(update) })
    update(toolWindowHeight)
  }
}