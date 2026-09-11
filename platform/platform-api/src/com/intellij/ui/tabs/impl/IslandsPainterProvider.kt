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

@ApiStatus.Internal
interface IslandsPainterProvider {
  companion object {
    @JvmStatic
    fun getInstance(): IslandsPainterProvider? = ApplicationManager.getApplication()?.serviceOrNull()
  }

  fun createCommonTabPainter(): TabPainterAdapter? = null
  fun useMacScrollBar(): Boolean = false
  fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? = null
  fun isRoundedTabDuringDrag(): Boolean = false

  fun getEditorTabComposedBgColor(
    component: JComponent,
    tabPainter: JBTabPainter,
    tabColor: Color?,
    active: Boolean,
    hovered: Boolean,
    selected: Boolean,
  ): Color? = null
}

@ApiStatus.Internal
interface UIThemeCustomization {
  // todo merge IslandsPainterProvider and InternalUICustomization in this one interface, register only one service in platform-impl
  // todo this is com.intellij.openapi.application.impl.islands.IslandsUICustomization implementing it
}