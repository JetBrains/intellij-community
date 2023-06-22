// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

interface DimensionRestrictions {
  fun getWidth(): Int?
  fun getHeight(): Int?

  class ScalingConstant(
    private val width: Int? = null,
    private val height: Int? = null
  ) : DimensionRestrictions {
    override fun getWidth(): Int? = width?.let(JBUIScale::scale)
    override fun getHeight(): Int? = height?.let(JBUIScale::scale)
  }

  class LinesHeight(
    private val component: JComponent,
    private val linesCount: Int,
    private val scalableWidth: Int? = null
  ) : DimensionRestrictions {
    override fun getWidth(): Int? = scalableWidth?.let(JBUIScale::scale)
    override fun getHeight(): Int = UIUtil.getLineHeight(component) * linesCount
  }

  object None : DimensionRestrictions {
    override fun getWidth(): Int? = null
    override fun getHeight(): Int? = null
  }
}