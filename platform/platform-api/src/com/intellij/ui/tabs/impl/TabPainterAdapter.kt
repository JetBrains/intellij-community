// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.impl.themes.TabTheme
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Graphics

interface TabPainterAdapter {
  @Internal
  fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl)

  val tabPainter: JBTabPainter

  fun getTabTheme(): TabTheme = tabPainter.getTabTheme()
}
