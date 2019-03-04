// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.util.ui.JBUI
import java.awt.Color

class TabTheme(
  val background: Color? = JBUI.CurrentTheme.DefaultTabs.background(),
  val borderColor: Color = JBUI.CurrentTheme.DefaultTabs.borderColor(),
  val underlineColor: Color = JBUI.CurrentTheme.DefaultTabs.underlineColor(),
  val inactiveUnderlineColor: Color = JBUI.CurrentTheme.DefaultTabs.inactiveUnderlineColor(),
  val hoverMaskColor: Color = JBUI.CurrentTheme.DefaultTabs.hoverMaskColor(),
  val inactiveMaskColor: Color = JBUI.CurrentTheme.DefaultTabs.inactiveMaskColor(),
  val underlineHeight: Int = JBUI.CurrentTheme.DefaultTabs.underlineHeight()
) {
  companion object {
    @JvmField
    val EDITOR = TabTheme(JBUI.CurrentTheme.EditorTabs.background(),
                          JBUI.CurrentTheme.EditorTabs.borderColor(),
                          JBUI.CurrentTheme.EditorTabs.underlineColor(),
                          JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor(),
                          JBUI.CurrentTheme.EditorTabs.hoverMaskColor(),
                          JBUI.CurrentTheme.EditorTabs.inactiveMaskColor(),
                          JBUI.CurrentTheme.EditorTabs.underlineHeight())
    @JvmField
    val TOOL_WINDOW = TabTheme(null,
                               JBUI.CurrentTheme.EditorTabs.borderColor(),
                               JBUI.CurrentTheme.EditorTabs.underlineColor(),
                               JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor(),
                               JBUI.CurrentTheme.EditorTabs.hoverMaskColor(),
                               JBUI.CurrentTheme.EditorTabs.inactiveMaskColor(),
                               JBUI.CurrentTheme.ToolWindow.underlineHeight())
  }
}
