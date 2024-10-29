// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl.actions

import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.actionSystem.EditorAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExpandCollapseToggleAction : EditorAction(object : BaseFoldingHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val line = editor.caretModel.logicalPosition.line

    val processor = Runnable {
      val region = FoldingUtil.findFoldRegionStartingAtLine(editor, line)
      if (region != null) {
        region.revertExpanding()
      }
      else {
        val offset = editor.caretModel.offset
        val regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset)

        regions.first().revertExpanding()
      }

    }
    editor.foldingModel.runBatchFoldingOperation(processor)
  }

  private fun FoldRegion.revertExpanding() {
    this.isExpanded = !this.isExpanded
  }
}), ActionRemoteBehaviorSpecification.Frontend