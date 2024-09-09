// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FilePathSplittingPolicy
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

class FilePathDiffTitleCustomizer(
  private val displayedPath: String,
  private val fullPath: @NlsSafe String = displayedPath,
  private val label: JComponent? = null,
) : DiffEditorTitleCustomizer {
  override fun getLabel(): JComponent {
    val revisionWithPath = JPanel(GridBagLayout())

    if (label != null) {
      revisionWithPath.add(label, GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        weightx = 0.0
        gridx = 0
        ipadx = scale(8)
      })
    }
    val pathLabel = DiffFilePathLabelWrapper(displayedPath, fullPath)
    revisionWithPath.add(pathLabel, GridBagConstraints().apply {
      fill = GridBagConstraints.BOTH
      weightx = 1.0;
      gridx = 1
    })
    return revisionWithPath
  }
}

@ApiStatus.Internal
class DiffFilePathLabelWrapper(val displayedPath: String, val fullPath: String) : JComponent() {
  private val wrappedLabel = DiffFilePathLabel(displayedPath, fullPath)

  init {
    layout = null
    add(wrappedLabel)
  }

  override fun doLayout() {
    wrappedLabel.size = size
    wrappedLabel.doLayout()
  }

  override fun getMinimumSize(): Dimension = wrappedLabel.minimumSize
  override fun getPreferredSize(): Dimension = wrappedLabel.preferredSize
  override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
}

private class DiffFilePathLabel(path: String, fullPath: @NlsSafe String) : JBLabel() {
  private val file = File(path)

  init {
    isAllowAutoWrapping = true
    setCopyable(true)
    toolTipText = fullPath
    foreground = UIUtil.getContextHelpForeground()
  }

  override fun setSize(d: Dimension) {
    super.setSize(d)
    text = SplitBySeparatorKeepFileNamePolicy.getOptimalTextForComponent(file, this, d.width)
  }

  // Fully managed by the parent.
  override fun validate() {}
  override fun invalidate() {}
  override fun revalidate() {}
}

private object SplitBySeparatorKeepFileNamePolicy : FilePathSplittingPolicy() {
  override fun getPresentableName(file: File, length: Int): String =
    if (length < file.name.length) file.name else FilePathSplittingPolicy.SPLIT_BY_SEPARATOR.getPresentableName(file, length)
}