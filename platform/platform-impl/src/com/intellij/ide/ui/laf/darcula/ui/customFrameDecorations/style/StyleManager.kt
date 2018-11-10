// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style

import javax.swing.JComponent

/**
 * @author graann on 22/06/2018
 */
class StyleManager {
  companion object {
    fun <T : JComponent> applyStyle(component: T, style: ComponentStyle<T>) {
      removeStyle(component)
      style.applyStyle(component)
    }

    fun <T : JComponent> removeStyle(component: T) {
      val propertyChangeListeners = component.getPropertyChangeListeners(ComponentStyle.ENABLED_PROPERTY)

      for (styleComponentListener in propertyChangeListeners) {
        if(styleComponentListener is ComponentStyle.StyleComponentListener<*>) {
          styleComponentListener.destroy()
        }
      }
    }
  }
}

