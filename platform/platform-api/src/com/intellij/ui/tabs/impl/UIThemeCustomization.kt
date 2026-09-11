// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Insets
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

@ApiStatus.Internal
interface UIThemeCustomization {
  companion object {
    private val isActivated = AtomicBoolean()

    @Volatile
    private var instance: UIThemeCustomization? = null // we do not use service because instance often accessed from hot paint path

    @JvmStatic
    fun getInstance(): UIThemeCustomization? = instance

    fun activate(manager: UIThemeCustomization?) {
      if (!isActivated.compareAndSet(false, true)) {
        return
      }

      instance = manager
    }
  }

  val commonTabPainterAdapter: TabPainterAdapter? get() = null
  val isMacScrollBar: Boolean get() = false
  val isRoundedTabDuringDrag: Boolean get() = false

  fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? = null

  fun getEditorTabComposedBgColor(
    component: JComponent,
    tabPainter: JBTabPainter,
    tabColor: Color?,
    active: Boolean,
    hovered: Boolean,
    selected: Boolean,
  ): Color? = null
}
