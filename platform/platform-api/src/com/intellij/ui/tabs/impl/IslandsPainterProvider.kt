// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.ui.tabs.JBTabsPosition
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets

@ApiStatus.Experimental
@ApiStatus.Internal
open class IslandsPainterProvider {
  companion object {
    @JvmStatic
    fun getInstance(): IslandsPainterProvider? = ApplicationManager.getApplication()?.serviceOrNull()
  }

  open fun createCommonTabPainter(): TabPainterAdapter? = null

  open fun useMacScrollBar(): Boolean = false

  open fun isTabOccupiesWholeHeight(): Boolean = true

  open fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? = null
}