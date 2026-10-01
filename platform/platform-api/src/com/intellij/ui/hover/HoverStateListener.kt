// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.hover

import java.awt.Component

abstract class HoverStateListener : HoverListener() {
  protected abstract fun hoverChanged(component: Component, hovered: Boolean)

  override fun mouseEntered(component: Component, x: Int, y: Int): Unit = hoverChanged(component, true)
  override fun mouseMoved(component: Component, x: Int, y: Int) {}
  override fun mouseExited(component: Component): Unit = hoverChanged(component, false)
}
