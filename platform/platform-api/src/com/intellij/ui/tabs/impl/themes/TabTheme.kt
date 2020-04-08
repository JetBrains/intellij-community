// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.themes

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.util.ui.JBUI
import java.awt.Color

interface TabTheme {
  val background: Color?
  val borderColor: Color
  val underlineColor: Color
  val inactiveUnderlineColor: Color
  val hoverBackground: Color
  val underlinedTabBackground: Color?
  val underlinedTabForeground: Color
  val underlineHeight: Int

  val hoverInactiveBackground: Color?
  val underlinedTabInactiveBackground: Color?
  val underlinedTabInactiveForeground: Color?
}

open class DefaultTabTheme : TabTheme {
  override val background: Color? get() = JBUI.CurrentTheme.DefaultTabs.background()
  override val borderColor: Color get() = JBUI.CurrentTheme.DefaultTabs.borderColor()
  override val underlineColor: Color get() = JBUI.CurrentTheme.DefaultTabs.underlineColor()
  override val inactiveUnderlineColor: Color get() = JBUI.CurrentTheme.DefaultTabs.inactiveUnderlineColor()
  override val hoverBackground: Color get() = JBUI.CurrentTheme.DefaultTabs.hoverBackground()
  override val underlinedTabBackground: Color? get() = JBUI.CurrentTheme.DefaultTabs.underlinedTabBackground()
  override val underlinedTabForeground: Color get() = JBUI.CurrentTheme.DefaultTabs.underlinedTabForeground()
  override val underlineHeight: Int get()= JBUI.CurrentTheme.DefaultTabs.underlineHeight()
  override val hoverInactiveBackground: Color?
    get() = hoverBackground
  override val underlinedTabInactiveBackground: Color?
    get() = underlinedTabBackground
  override val underlinedTabInactiveForeground: Color
    get() = underlinedTabForeground
}

class EditorTabTheme : TabTheme {
  val globalScheme: EditorColorsScheme
    get() = EditorColorsManager.getInstance().globalScheme

  override val background: Color?
    get() = JBUI.CurrentTheme.EditorTabs.background()
  override val borderColor: Color
    get() = JBUI.CurrentTheme.EditorTabs.borderColor()

  override val underlineColor: Color
    get() = globalScheme.getColor(EditorColors.TAB_UNDERLINE) ?: JBUI.CurrentTheme.EditorTabs.underlineColor()

  override val inactiveUnderlineColor: Color
    get() = globalScheme.getColor(EditorColors.TAB_UNDERLINE_INACTIVE) ?: JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor()

  override val hoverBackground: Color
    get() = JBUI.CurrentTheme.EditorTabs.hoverBackground()
  override val underlinedTabBackground: Color?
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED).backgroundColor?: JBUI.CurrentTheme.EditorTabs.underlinedTabBackground()

  override val underlinedTabForeground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED).foregroundColor?: JBUI.CurrentTheme.EditorTabs.underlinedTabForeground()

  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.EditorTabs.underlineHeight()

  override val hoverInactiveBackground: Color?
    get() = hoverBackground

  override val underlinedTabInactiveBackground: Color?
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED_INACTIVE).backgroundColor?: underlinedTabBackground

  override val underlinedTabInactiveForeground: Color
    get() = globalScheme.getAttributes(EditorColors.TAB_SELECTED_INACTIVE).foregroundColor?: underlinedTabForeground

  val inactiveColoredFileBackground: Color?
    get() = JBUI.CurrentTheme.EditorTabs.inactiveColoredFileBackground()
}

internal class ToolWindowTabTheme : DefaultTabTheme() {
  override val background: Color?
    get() = null
  override val borderColor: Color
    get() = JBUI.CurrentTheme.ToolWindow.borderColor()
  override val underlineColor: Color
    get() = JBUI.CurrentTheme.ToolWindow.underlineColor()
  override val inactiveUnderlineColor: Color
    get() = JBUI.CurrentTheme.ToolWindow.inactiveUnderlineColor()
  override val hoverBackground: Color
    get() = JBUI.CurrentTheme.ToolWindow.hoverBackground()
  override val underlinedTabBackground: Color?
    get() = JBUI.CurrentTheme.ToolWindow.underlinedTabBackground()
  override val underlinedTabForeground: Color
    get() = JBUI.CurrentTheme.ToolWindow.underlinedTabForeground()
  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.ToolWindow.underlineHeight()

  override val hoverInactiveBackground: Color?
    get() = JBUI.CurrentTheme.ToolWindow.hoverInactiveBackground()
  override val underlinedTabInactiveBackground: Color?
    get() = JBUI.CurrentTheme.ToolWindow.underlinedTabInactiveBackground()
  override val underlinedTabInactiveForeground: Color
    get() = JBUI.CurrentTheme.ToolWindow.underlinedTabInactiveForeground()
}

internal class DebuggerTabTheme : DefaultTabTheme() {
  override val underlineHeight: Int
    get() = JBUI.CurrentTheme.DebuggerTabs.underlineHeight()
  override val underlinedTabBackground: Color?
    get() = JBUI.CurrentTheme.DebuggerTabs.underlinedTabBackground()
}