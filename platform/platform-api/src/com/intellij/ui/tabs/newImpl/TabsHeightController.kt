// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap
import javax.swing.JComponent

class TabsHeightController {
  companion object {

    private val heightMap = ViewableMap<JComponent, Int>()
    private var lifetimeDefinition: LifetimeDefinition? = null
    private var lastValue = TabsUtil.getTabsHeight(JBUI.CurrentTheme.ToolWindow.tabVerticalPadding())

    @JvmStatic
    val toolWindowHeight = Property(lastValue)

    @JvmStatic
    fun registerHeight(comp: JComponent, height: Int) {
      if(heightMap.isEmpty()) {
        lifetimeDefinition = LifetimeDefinition()

        lifetimeDefinition?.let { lt ->
          heightMap.advise(lt.lifetime) {
            val value = heightMap.maxBy { it.value }?.value
            value?.let {
              lastValue = it
            }
            toolWindowHeight.set(value ?: lastValue)
          }
        }
      }

      heightMap[comp] = height
    }

    @JvmStatic
    fun unregister(comp: JComponent) {
      heightMap.remove(comp)

      if(heightMap.isEmpty()) {
        lifetimeDefinition?.let {
          if(it.isAlive)
            it.terminate()
        }
      }
    }
  }
}