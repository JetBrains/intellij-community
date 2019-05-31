// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.swing.sizeProperty
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.rd.createNestedDisposable
import javax.swing.JComponent

class TabsHeightController {
  companion object {
    private val heightMap = ViewableMap<JComponent, Int>()
    private val adjectives = ViewableMap<JComponent, (Int) -> Unit>()

    private var ld = LifetimeDefinition()
    private val toolWindowHeightProperty = Property(TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding()))

    init {
      heightMap.advise(ld) {
        val value = heightMap.maxBy { it.value }?.value
        value?.let {
          toolWindowHeightProperty.set(if(it > 0) it else TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding()))
        }
      }

      toolWindowHeightProperty.advise(ld) {
        for (entry in adjectives) {
          entry.value(it)
        }
      }
    }

    @JvmStatic
    fun registerActive(comp: JComponent, parentDisposable: Disposable) {
      val lifetime = createNestedLifeTime(parentDisposable)

      lifetime.bracket({
                         comp.sizeProperty().advise(lifetime) {
                           heightMap[comp] = it.height
                         }
                         if (comp.height > 0)
                           heightMap[comp] = comp.height
                       },
                       { heightMap.remove(comp) })

    }

    @JvmStatic
    fun registerAdjective(comp: JComponent, update: (Int) -> Unit, parentDisposable: Disposable) {
      val lifetime = createNestedLifeTime(parentDisposable)

      lifetime.bracket({
                         adjectives[comp] = update
                         update(toolWindowHeightProperty.value)
                       },
                       { adjectives.remove(comp) })
    }

    private fun createNestedLifeTime(parentDisposable: Disposable): Lifetime {
      val ds = Disposer.newDisposable()
      val nestedDisposable = ld.createNestedDisposable()
      Disposer.register(nestedDisposable, ds)
      Disposer.register(parentDisposable, nestedDisposable)
      return ds.createLifetime()
    }
  }
}