// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl

class UpdateContext(val force: Boolean = false) {
  private val foldingOperations = mutableListOf<(FoldingModelEx) -> Unit>()
  private val inlayOperations = mutableListOf<(InlayModel) -> Unit>()

  fun addFoldingOperation(block: (FoldingModelEx) -> Unit) {
    foldingOperations.add(block)
  }

  fun addInlayOperation(block: (InlayModel) -> Unit) {
    inlayOperations.add(block)
  }

  fun applyUpdates(editor: Editor) {
    if (editor.isDisposed) return

    if (foldingOperations.isNotEmpty()) {
      val foldingModel = RemovalTrackingFoldingModel(editor.foldingModel as FoldingModelImpl)
      foldingModel.runBatchFoldingOperation({
                                              foldingOperations.forEach { it(foldingModel) }
                                            }, true, false)
    }

    if (inlayOperations.isNotEmpty()) {
      val inlayModel = editor.inlayModel
      inlayModel.execute(true) {
        inlayOperations.forEach { it(inlayModel) }
      }
    }
  }

  /**
   * [FoldingModelEx] implementation tracking fold region removal and clearing offsets cache before custom folding creation.
   * Else there will be an exception because of an invalid folding region.
   */
  private class RemovalTrackingFoldingModel(private val model: FoldingModelImpl) : FoldingModelEx by model {
    private var resetCache = false

    override fun removeFoldRegion(region: FoldRegion) {
      resetCache = true
      model.removeFoldRegion(region)
    }

    override fun addCustomLinesFolding(startLine: Int, endLine: Int, renderer: CustomFoldRegionRenderer): CustomFoldRegion? {
      if (resetCache) {
        model.updateCachedOffsets()
        resetCache = false
      }
      return model.addCustomLinesFolding(startLine, endLine, renderer)
    }
  }
}