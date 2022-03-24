// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent

internal class CombinedLazyDiffViewer(val requestProducer: DiffRequestProducer, size: Dimension? = null) : DiffViewer {

  private val loadingPanel = JBLoadingPanel(BorderLayout(), this)
    .apply {
      add(JBUI.Panels.simplePanel()
            .apply {
              background = UIUtil.getListBackground()
              preferredSize = if (size != null) size else HEIGHT.get().let { height -> Dimension(height, height) }
            })
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
    val HEIGHT = JBValue.UIInteger("CombinedLazyDiffViewer.height", 50)
  }
}
