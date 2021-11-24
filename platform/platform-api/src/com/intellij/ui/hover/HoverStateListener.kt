// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.hover

import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Experimental
abstract class HoverStateListener : HoverListener() {
  protected abstract fun hoverChanged(component: Component, hovered: Boolean)

  override fun mouseEntered(component: Component, x: Int, y: Int) = hoverChanged(component, true)
  override fun mouseMoved(component: Component, x: Int, y: Int) = Unit
  override fun mouseExited(component: Component) = hoverChanged(component, false)
}
