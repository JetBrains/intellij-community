// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.create

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.ListCellRenderer
import javax.swing.text.AttributeSet
import javax.swing.text.PlainDocument

object CodeReviewCreateReviewUIUtil {
  private val titleFont
    get() = JBUI.Fonts.label(16f)

  fun JBTextArea.applyDefaults() {
    font = titleFont
    background = UIUtil.getListBackground()
    lineWrap = true
  }

  fun createTitleEditor(emptyText: String = ""): JBTextArea = JBTextArea(SingleLineDocument()).apply {
    applyDefaults()
    this.emptyText.text = emptyText
    preferredSize = Dimension(0, JBUI.scale(font.size * 5))
  }.also {
    CollaborationToolsUIUtil.registerFocusActions(it)
  }

  fun createCommitListCellRenderer(): ListCellRenderer<VcsCommitMetadata> = CodeReviewTwoLinesCommitRenderer()
}

@ApiStatus.Internal
open class SingleLineDocument : PlainDocument() {
  override fun insertString(offs: Int, str: String, a: AttributeSet?) {
    // filter new lines
    val withoutNewLines = StringUtil.replace(str, "\n", "")
    super.insertString(offs, withoutNewLines, a)
  }
}
