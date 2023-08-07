// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBValue
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent

internal class CombinedDiffLoadingBlock(size: Dimension? = null) : DiffViewer {
  private val loadingPanel: JBLoadingPanel = object : JBLoadingPanel(BorderLayout(), this, CombinedDiffUI.LOADING_BLOCK_PROGRESS_DELAY) {
    init {
      background = CombinedDiffUI.LOADING_BLOCK_BACKGROUND
    }

    override fun getPreferredSize(): Dimension = size ?: Dimension(super.getPreferredSize().width, DEFAULT_LOADING_BLOCK_HEIGHT.get())
  }

  override fun getComponent(): JComponent = loadingPanel

  override fun getPreferredFocusedComponent(): JComponent = loadingPanel

  override fun init(): FrameDiffTool.ToolbarComponents {
    loadingPanel.startLoading()
    return FrameDiffTool.ToolbarComponents()
  }

  override fun dispose() {
    loadingPanel.stopLoading()
  }

  companion object {
    val DEFAULT_LOADING_BLOCK_HEIGHT = JBValue.UIInteger("CombinedLazyDiffViewer.height", 150)
  }
}
