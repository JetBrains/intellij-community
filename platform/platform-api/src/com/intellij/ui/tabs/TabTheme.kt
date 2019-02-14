// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.util.ui.JBUI
import java.awt.Color

class TabTheme(
  val background: Color? = JBUI.CurrentTheme.DefaultTabs.background(),
  val borderColor: Color = JBUI.CurrentTheme.DefaultTabs.borderColor(),
  val underline: Color = JBUI.CurrentTheme.DefaultTabs.underlineColor(),
  val inactiveUnderline: Color = JBUI.CurrentTheme.DefaultTabs.inactiveUnderlineColor(),
  val hoverOverlayColor: Color = JBUI.CurrentTheme.DefaultTabs.hoverOverlayColor(),
  val unselectedOverlayColor: Color = JBUI.CurrentTheme.DefaultTabs.unselectedOverlayColor(),
  val underlineThickness: Int = JBUI.CurrentTheme.DefaultTabs.underlineThickness()
) {
  companion object {
    val EDITOR = TabTheme(JBUI.CurrentTheme.EditorTabs.background(),
                          JBUI.CurrentTheme.EditorTabs.borderColor(),
                          JBUI.CurrentTheme.EditorTabs.underlineColor(),
                          JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor(),
                          JBUI.CurrentTheme.EditorTabs.hoverOverlayColor(),
                          JBUI.CurrentTheme.EditorTabs.unselectedOverlayColor(),
                          JBUI.CurrentTheme.EditorTabs.underlineThickness())

    val TOOL_WINDOW = TabTheme(null,
                               JBUI.CurrentTheme.EditorTabs.borderColor(),
                               JBUI.CurrentTheme.EditorTabs.underlineColor(),
                               JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor(),
                               JBUI.CurrentTheme.EditorTabs.hoverOverlayColor(),
                               JBUI.CurrentTheme.EditorTabs.unselectedOverlayColor(),
                               JBUI.CurrentTheme.ToolWindow.underlineThickness())
  }
}
