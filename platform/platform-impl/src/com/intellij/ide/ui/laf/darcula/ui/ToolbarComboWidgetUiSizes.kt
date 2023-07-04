// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.ui.scale.JBUIScale

object ToolbarComboWidgetUiSizes {

  @JvmStatic
  val separatorGap: Int get() = JBUIScale.scale(3)

  @JvmStatic
  val gapAfterLeftIcons: Int get() = JBUIScale.scale(6)

  @JvmStatic
  val gapBeforeRightIcons: Int get() = JBUIScale.scale(4)

  @JvmStatic
  val gapBeforeExpandIcon: Int get() = JBUIScale.scale(3)
}