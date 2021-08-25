// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent
import javax.swing.JPanel

interface CombinedDiffBlock : Disposable {
  val content: CombinedDiffBlockContent

  val header: JComponent
  val body: JComponent
  val component: JComponent
}

class CombinedDiffBlockContent(val viewer: FrameDiffTool.DiffViewer, val path: FilePath, val fileStatus: FileStatus)

interface CombinedDiffBlockFactory {
  companion object {
    private val EP_COMBINED_DIFF_BLOCK_FACTORY =
      ExtensionPointName<CombinedDiffBlockFactory>("com.intellij.diff.tools.combined.diffBlockFactory")

    fun findApplicable(content: CombinedDiffBlockContent): CombinedDiffBlockFactory? {
      return EP_COMBINED_DIFF_BLOCK_FACTORY.findFirstSafe { it.isApplicable(content) }
    }
  }

  fun isApplicable(content: CombinedDiffBlockContent): Boolean
  fun createBlock(content: CombinedDiffBlockContent, withBorder: Boolean): CombinedDiffBlock
}

class CombinedSimpleDiffBlockFactory : CombinedDiffBlockFactory {
  override fun isApplicable(content: CombinedDiffBlockContent) = true //default factory

  override fun createBlock(content: CombinedDiffBlockContent, withBorder: Boolean): CombinedDiffBlock =
    CombinedSimpleDiffBlock(content, withBorder)
}

private class CombinedSimpleDiffHeader(path: FilePath, withBorder: Boolean) : BorderLayoutPanel() {
  init {
    background = UIUtil.getListBackground()
    if (withBorder) {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    val parentPath = path.parentPath?.let(FilePath::getPresentableUrl)?.let(FileUtil::getLocationRelativeToUserHome)
    val textComponent = SimpleColoredComponent().append(path.name).apply {
      if (parentPath != null) {
        append(FontUtil.spaceAndThinSpace() + parentPath, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
    addToCenter(textComponent)
  }
}

private class CombinedSimpleDiffBlock(override val content: CombinedDiffBlockContent, withBorder: Boolean) :
  JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true)), CombinedDiffBlock {

  override val header = CombinedSimpleDiffHeader(content.path, withBorder)
  override val body = content.viewer.component

  init {
    Disposer.register(this, content.viewer)
    add(header)
    add(body)
  }

  override val component = this
  override fun dispose() {}
}
