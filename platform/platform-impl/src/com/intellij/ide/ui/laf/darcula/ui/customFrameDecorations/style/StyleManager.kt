// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style

import javax.swing.JComponent

/**
 * @author graann on 22/06/2018
 */
class StyleManager {
  companion object {
    private const val STYLE_PROPERTY = "STYLE_PROPERTY"
    fun <T : JComponent> applyStyle(component: T, style: ComponentStyle<T>) {
      removeStyle(component)
      val removeStyleListener = style.applyStyleSnapshot(component)
      component.putClientProperty(STYLE_PROPERTY, removeStyleListener)
    }

    fun <T : JComponent> removeStyle(component: T) {
      val removeStyleListener = component.getClientProperty(STYLE_PROPERTY)
      if (removeStyleListener != null && removeStyleListener is RemoveStyleListener) {
        removeStyleListener.remove()
      }
    }
  }
}

