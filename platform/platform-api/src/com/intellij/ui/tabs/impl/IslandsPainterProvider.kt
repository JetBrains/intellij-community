// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Insets
import javax.swing.JComponent

@ApiStatus.Experimental
@ApiStatus.Internal
open class IslandsPainterProvider {
  companion object {
    @JvmStatic
    fun getInstance(): IslandsPainterProvider? = ApplicationManager.getApplication()?.serviceOrNull()
  }

  open fun createCommonTabPainter(): TabPainterAdapter? = null

  open fun useMacScrollBar(): Boolean = false

  open fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? = null

  open fun isRoundedTabDuringDrag(): Boolean = false

  open fun getEditorTabComposedBgColor(
    component: JComponent,
    tabPainter: JBTabPainter,
    tabColor: Color?,
    active: Boolean,
    hovered: Boolean,
    selected: Boolean,
  ): Color? = null
}