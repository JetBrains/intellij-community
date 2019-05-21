// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl.themes

import com.intellij.util.ui.JBUI
import java.awt.Color

interface TabTheme {
  val background: Color?
  val borderColor: Color
  val underlineColor: Color
  val inactiveUnderlineColor: Color
  val hoverBackground: Color
  val inactiveColoredFileBackground: Color?
  val underlinedTabBackground: Color?
  val underlinedTabForeground: Color?
  val underlineHeight: Int
}

open class DefaultTabTheme : TabTheme {
  override val background: Color? get() = JBUI.CurrentTheme.DefaultTabs.background()
  override val borderColor: Color get() = JBUI.CurrentTheme.DefaultTabs.borderColor()
  override val underlineColor: Color get() = JBUI.CurrentTheme.DefaultTabs.underlineColor()
  override val inactiveUnderlineColor: Color get() = JBUI.CurrentTheme.DefaultTabs.inactiveUnderlineColor()
  override val hoverBackground: Color get() = JBUI.CurrentTheme.DefaultTabs.hoverBackground()
  override val inactiveColoredFileBackground: Color? get() = null
  override val underlinedTabBackground: Color? get() = null
  override val underlinedTabForeground: Color? get() = JBUI.CurrentTheme.DefaultTabs.underlinedTabForeground()
  override val underlineHeight: Int get()= JBUI.CurrentTheme.DefaultTabs.underlineHeight()
}

class EditorTabTheme : TabTheme {
  override val background: Color?
    get() = JBUI.CurrentTheme.EditorTabs.background()
  override val borderColor: Color
    get() = JBUI.CurrentTheme.EditorTabs.borderColor()
  override val underlineColor: Color
    get() = JBUI.CurrentTheme.EditorTabs.underlineColor()
  override val inactiveUnderlineColor: Color
    get() = JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor()
  override val hoverBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.hoverBackground()
  override val inactiveColoredFileBackground: Color?
    get() = JBUI.CurrentTheme.EditorTabs.inactiveColoredFileBackground()
  override val underlinedTabBackground: Color?
    get() = JBUI.CurrentTheme.EditorTabs.underlinedTabBackground()
  override val underlinedTabForeground: Color?
    get() = JBUI.CurrentTheme.EditorTabs.underlinedTabForeground()
  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.EditorTabs.underlineHeight()
}

class ToolWindowTabTheme : DefaultTabTheme() {
  override val background: Color?
    get() = null
  override val underlinedTabBackground: Color?
    get() = null
  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.ToolWindow.underlineHeight()
}

class DebuggerTabTheme : DefaultTabTheme() {
  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.DebuggerTabs.underlineHeight()
}