// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import com.intellij.util.applyIf
import com.intellij.util.ui.FilePathSplittingPolicy
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.io.File
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JPanel

class FilePathDiffTitleCustomizer(
  private val displayedPath: String,
  private val fullPath: @NlsSafe String = displayedPath,
  private val label: JComponent? = null,
) : DiffEditorTitleCustomizer {
  override fun getLabel(): JComponent {
    val revisionWithPath = JPanel(null)
    val grLayout = GroupLayout(revisionWithPath)
    revisionWithPath.layout = grLayout

    val pathLabel = FilePathLabelWrapper(
      FilePathLabel(displayedPath).apply {
        isAllowAutoWrapping = true
        setCopyable(true)
        toolTipText = fullPath
        foreground = UIUtil.getContextHelpForeground()
      }
    )

    val gap = JBUI.scale(8)
    grLayout.setHorizontalGroup(
      grLayout.createSequentialGroup()
        .applyIf(label != null) {
          addComponent(label, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).addGap(gap, gap, gap)
        }
        .addComponent(pathLabel, 0, GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
    )

    grLayout.setVerticalGroup(
      grLayout.createSequentialGroup()
        .addGroup(grLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .applyIf(label != null) { addComponent(label) }
                    .addComponent(pathLabel))
    )

    return revisionWithPath
  }
}

private class FilePathLabelWrapper(private val wrappedLabel: FilePathLabel) : JComponent() {
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

private class FilePathLabel(path: String) : JBLabel() {
  private val file = File(path)

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