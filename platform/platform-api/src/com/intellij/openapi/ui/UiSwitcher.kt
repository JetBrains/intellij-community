// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.util.Key
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent

/**
 * Defines switcher component which can change visibility state of component
 * in UI view, for example collapsible groups in Kotlin UI DSL.
 * Use [UiSwitcher.append] to add a switcher to a component. At the same time component can belong to several UiSwitcher
 *
 * @see UIUtil#showComponent(Component)
 */
@ApiStatus.Experimental
interface UiSwitcher {

  /**
   * Returns false if the component assigned to this UiSwitcher cannot be shown
   */
  fun show(): Boolean

  companion object {

    private val UI_SWITCHER = Key.create<UiSwitcher>("com.intellij.openapi.ui.UI_SWITCHER")

    @JvmStatic
    fun append(component: JComponent, uiSwitcher: UiSwitcher) {
      val existingSwitcher = component.getUserData(UI_SWITCHER)
      component.putUserData(UI_SWITCHER, if (existingSwitcher == null) uiSwitcher
      else UnionUiSwitcher(
        listOf(existingSwitcher, uiSwitcher)))
    }

    /**
     * Tries to show the component in UI hierarchy:
     * * Expands collapsable groups if the component is inside such groups
     */
    @JvmStatic
    fun show(component: Component) {
      var c: Component?  = component
      while (c != null && !c.isShowing) {
        if (c is JComponent) {
          val uiSwitcher = c.getUserData(UI_SWITCHER)
          if (uiSwitcher?.show() == false) {
            return
          }
        }
        c = c.parent
      }
    }
  }
}

private class UnionUiSwitcher(private val uiSwitchers: List<UiSwitcher>) : UiSwitcher {

  override fun show(): Boolean {
    return uiSwitchers.all { it.show() }
  }
}
