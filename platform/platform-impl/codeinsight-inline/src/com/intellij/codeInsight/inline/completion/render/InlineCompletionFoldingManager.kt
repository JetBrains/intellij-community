// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt

// TODO what if something in line is already folded?
internal class InlineCompletionFoldingManager private constructor(private val editor: Editor) : Disposable {

  private val document: Document
    get() = editor.document

  private val foldingModel: FoldingModel
    get() = editor.foldingModel

  private val foldedLines = mutableMapOf<Int, FoldRegion>()

  /**
   * Folds the text for the range `[startOffset, lineEndOffset)`.
   *
   * * If [startOffset] is at the line end, nothing happens
   * * This method cannot be called for the same line twice
   * * It folds with `placeholder = ""`
   * * No expansion is possible
   */
  @RequiresEdt
  fun foldLineEnd(startOffset: Int, disposable: Disposable): TextRange? {
    ThreadingAssertions.assertEventDispatchThread()

    if (!isFoldingSupported(editor)) {
      return null
    }

    val foldingModel = foldingModel as FoldingModelEx

    val lineNumber = document.getLineNumber(startOffset)
    val lineEnd = document.getLineEndOffset(lineNumber)
    if (startOffset == lineEnd) {
      return null
    }

    if (lineNumber in foldedLines) {
      LOG.error("[Inline Completion] Incorrect state of folding. The same line $lineNumber is folded twice.")
      return null
    }

    foldingModel.runBatchFoldingOperation {
      val foldRegion = foldingModel.createFoldRegion(
        /* startOffset = */ startOffset,
        /* endOffset = */ lineEnd,
        /* placeholder = */ "",
        /* group = */ null,
        /* neverExpands = */ true
      )
      if (foldRegion != null) {
        foldRegion.putUserData(FoldingKeys.ADDITIONAL_CARET_POSITION_FOR_EMPTY_PLACEHOLDER, true)
        foldRegion.isExpanded = false
        foldRegion.isGreedyToRight = false
        foldRegion.isGreedyToLeft = false
        foldedLines[lineNumber] = foldRegion
        disposable.whenDisposed {
          foldingModel.runBatchFoldingOperation {
            foldingModel.removeFoldRegion(foldRegion)
          }
          foldedLines.remove(lineNumber)
        }
      }
      else {
        LOG.debug { "[Inline Completion] Failed to fold line $lineNumber." }
      }
    }


    return foldedLines[lineNumber]?.textRange
  }

  /**
   * If this manager folded [offset], the line end offset is returned. Otherwise, [offset] is returned.
   */
  @RequiresEdt
  fun firstNotFoldedOffset(offset: Int): Int {
    val foldRegion = getFoldingRegion(offset) ?: return offset
    return if (foldRegion.textRange.contains(offset)) foldRegion.endOffset else offset
  }

  /**
   * If this manager folded [offset], the first folded offset on this line is returned. Otherwise, [offset] is returned.
   *
   * Exceptional case: if [offset] points to the very end of the line, we still return the start of the folded region, however,
   * the line end is never folded actually. The reason is that we might have rendered a multiline at the previous offset while the
   * line ending was folded. When we start to render anything right after the folded range, we'd like to continue rendering at the
   * same multiline state. See IJPL-183620.
   */
  @RequiresEdt
  fun offsetOfFoldStart(offset: Int): Int {
    val foldRegion = getFoldingRegion(offset) ?: return offset
    val range = foldRegion.textRange
    return if (range.contains(offset) || range.endOffset == offset) foldRegion.startOffset else offset
  }

  private fun getFoldingRegion(offset: Int): FoldRegion? {
    ThreadingAssertions.assertEventDispatchThread()
    return foldedLines[document.getLineNumber(offset)]
  }

  override fun dispose() {
    if (foldedLines.isNotEmpty()) {
      LOG.error("[Inline Completion] Incorrect state of folding. Some folded regions are not disposed.")
      clear()
    }
  }

  private fun clear() {
    foldingModel.runBatchFoldingOperation {
      foldedLines.forEach { (_, region) ->
        foldingModel.removeFoldRegion(region)
      }
    }
    foldedLines.clear()
  }

  companion object : InlineCompletionComponentFactory<InlineCompletionFoldingManager>() {
    private val KEY = Key<InlineCompletionFoldingManager>("inline.completion.folding.manager")
    private val LOG = thisLogger()

    private fun isFoldingSupported(editor: Editor): Boolean {
      if (!ClientId.currentOrNull.isLocal) {
        // Folding will happen on frontend
        return false
      }
      val foldingModel = editor.foldingModel
      return foldingModel is FoldingModelEx && foldingModel.isFoldingEnabled
    }

    override fun create(editor: Editor) = InlineCompletionFoldingManager(editor)

    override val key: Key<InlineCompletionFoldingManager>
      get() = KEY
  }
}
