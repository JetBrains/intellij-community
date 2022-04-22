// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.util.Key
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent

/**
 * Defines switcher component which can change visibility state of component
 * in total UI view. For example: tabs selector, collapsable groups, etc.
 *
 * Switcher component can implement this interface, or switcher may be
 * in component's context which should be accessible by [UI_SWITCHER] key.
 *
 * @see com.intellij.openapi.ui.putUserData
 * @see javax.swing.JComponent.putClientProperty
 * @see UiSwitcher.show
 */
@ApiStatus.Experimental
interface UiSwitcher {

  /**
   * Checks that [component] is administrated by this switcher, and changes switcher state
   * to make the component visible.
   *
   * For example select tab with component or expand collapsable
   * group if component is located in group.
   */
  fun show(component: Component)

  companion object {

    val UI_SWITCHER = Key.create<UiSwitcher>("com.intellij.openapi.ui.UI_SWITCHER")

    /**
     * Finds and applies all switchers which can show [component].
     */
    fun show(component: Component) {
      for (currentComponent in UIUtil.uiParents(component, false)) {
        val container = currentComponent.parent ?: return
        getSwitcher(container)?.show(currentComponent)
        for (sibling in UIUtil.uiChildren(container)) {
          getSwitcher(sibling)?.show(currentComponent)
        }
      }
    }

    private fun getSwitcher(component: Component): UiSwitcher? {
      return when (component) {
        is UiSwitcher -> component
        is JComponent -> component.getUserData(UI_SWITCHER)
        else -> null
      }
    }
  }
}