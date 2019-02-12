// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.util.ui.JBUI
import java.awt.Color

class TabTheme(
  val background: Color? = JBUI.CurrentTheme.EditorTabs.backgroundColor(),
  val borderColor: Color = JBUI.CurrentTheme.EditorTabs.borderColor(),
  val underline: Color = JBUI.CurrentTheme.EditorTabs.underlineColor(),
  val inactiveUnderline: Color = JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor(),
  val hoverOverlayColor: Color = JBUI.CurrentTheme.EditorTabs.hoverOverlayColor(),
  val unselectedOverlayColor: Color = JBUI.CurrentTheme.EditorTabs.unselectedOverlayColor(),
  val underlineThickness : Int = JBUI.scale(2)
) {

  companion object {
    @JvmStatic
    val TOOLWINDOW_TAB = TabTheme(background = null, underlineThickness = JBUI.scale(3))

    @JvmStatic
    val EDITOR_TAB = TabTheme(underlineThickness = JBUI.scale(3))

    @JvmStatic
    val DEFAULT_TAB = TabTheme()
  }
}