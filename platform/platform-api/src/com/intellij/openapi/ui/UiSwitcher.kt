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

    private val UI_SWITCHERS = Key.create<Set<UiSwitcher>>("com.intellij.openapi.ui.UI_SWITCHERS")

    @JvmStatic
    fun append(component: JComponent, uiSwitcher: UiSwitcher) {
      val uiSwitchers = getUiSwitchers(component)
      component.putUserData(UI_SWITCHERS, uiSwitchers + uiSwitcher)
    }

    @JvmStatic
    fun appendAll(component: JComponent, uiSwitchers: Set<UiSwitcher>) {
      val myUiSwitchers = getUiSwitchers(component)
      component.putUserData(UI_SWITCHERS, myUiSwitchers + uiSwitchers)
    }

    @JvmStatic
    fun removeAll(component: JComponent, uiSwitchers: Set<UiSwitcher>) {
      val myUiSwitchers = getUiSwitchers(component)
      component.putUserData(UI_SWITCHERS, (myUiSwitchers - uiSwitchers).ifEmpty { null })
    }

    /**
     * Tries to show the component in UI hierarchy:
     * * Expands collapsable groups if the component is inside such groups
     */
    @JvmStatic
    fun show(component: Component) {
      var c: Component? = component
      while (c != null && !c.isShowing) {
        if (c is JComponent) {
          val uiSwitchers = getUiSwitchers(c)
          for (uiSwitcher in uiSwitchers) {
            uiSwitcher.show()
          }
        }
        c = c.parent
      }
    }

    private fun getUiSwitchers(component: JComponent): Set<UiSwitcher> {
      return component.getUserData(UI_SWITCHERS) ?: mutableSetOf()
    }
  }
}
