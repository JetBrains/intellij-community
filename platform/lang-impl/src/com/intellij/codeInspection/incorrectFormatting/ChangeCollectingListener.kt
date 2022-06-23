// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.lang.ASTNode
import com.intellij.lang.VirtualFormattingListener
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile


class ChangeCollectingListener(val file: PsiFile, val originalText: String) : VirtualFormattingListener {
  private val changes = arrayListOf<FormattingChange>()

  fun getChanges(): List<FormattingChange> = changes

  override fun shiftIndentInsideRange(node: ASTNode?, range: TextRange, indent: Int) {
    changes.add(ShiftIndentChange(file, range, node, indent))
  }

  override fun replaceWhiteSpace(textRange: TextRange, whiteSpace: String) {
    changes.add(ReplaceChange(file, textRange, whiteSpace))
  }

}
