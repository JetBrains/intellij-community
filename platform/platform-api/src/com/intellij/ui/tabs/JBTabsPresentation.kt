// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.TimedDeadzone
import java.awt.Insets

interface JBTabsPresentation {
  var isHideTabs: Boolean

  var showBorder: Boolean

  fun setPaintFocus(paintFocus: Boolean): JBTabsPresentation

  fun setSideComponentVertical(vertical: Boolean): JBTabsPresentation

  fun setSideComponentOnTabs(onTabs: Boolean): JBTabsPresentation

  fun setSideComponentBefore(before: Boolean): JBTabsPresentation

  fun setSingleRow(singleRow: Boolean): JBTabsPresentation

  fun setUiDecorator(decorator: UiDecorator?): JBTabsPresentation

  fun setPaintBlocked(blocked: Boolean, takeSnapshot: Boolean)

  fun setInnerInsets(innerInsets: Insets): JBTabsPresentation

  fun setFocusCycle(root: Boolean): JBTabsPresentation?

  fun setToDrawBorderIfTabsHidden(draw: Boolean): JBTabsPresentation

  fun setTabLabelActionsAutoHide(autoHide: Boolean): JBTabsPresentation

  fun setTabLabelActionsMouseDeadzone(length: TimedDeadzone.Length): JBTabsPresentation

  fun setTabsPosition(position: JBTabsPosition): JBTabsPresentation

  val tabsPosition: JBTabsPosition

  fun setTabDraggingEnabled(enabled: Boolean): JBTabsPresentation

  fun setAlphabeticalMode(alphabeticalMode: Boolean): JBTabsPresentation

  fun setSupportsCompression(supportsCompression: Boolean): JBTabsPresentation

  fun setFirstTabOffset(offset: Int)

  fun setEmptyText(text: @NlsContexts.StatusText String?): JBTabsPresentation
}
