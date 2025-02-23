// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.render.InlineCompletionEditorTextUtils.getBlocksForRealText
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.removeUserData
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

/**
 * Accumulates all the text to be rendered at one offset and renders them.
 *
 * **Streaming**
 *
 * To be able to render streaming text, all blocks are split line by line.
 *
 * When a new text part comes, the last line is changed, while the previous lines stay the same.
 * Even if an element has multiline text, it's split into lines and they are rendered one by one.
 *
 * Rules:
 * * Disposing of the whole text happens only when all the 'children' elements are disposed.
 *   It relies on the internal implementation of the inline completion: all elements are always disposed at once.
 * * Each 'child' element returns [Rectangle] that represents the whole text. It may be fixed at some point.
 *
 * **Folding**
 *
 * The problem situation. When we have a multiline completion and some symbols on the right to the caret, they are not shifted to the
 * bottom with inlays. They must be, but it's not possible with inlays.
 * So, we hide the symbols on the right using folding, and render the same symbols using inlays
 * at correct positions. See [InlineCompletionFoldingManager].
 *
 * How does it work?
 * Imagine, we have a first request to render a multiline at some `offset` in a line. Then:
 * * We hide `[offset, lineEndOffset)`
 * * We render the completion at the offset `lineEndOffset - 1`
 * * The `offset` becomes `renderOffset`.
 * * We render the hidden symbols with inlays in the same offset
 * * If we have another request in the same line (no matter which offset), we re-direct this request to this manager with `renderOffset`.
 *   For that, we pass an additional `initialOffset`, where the new request comes from. If `initialOffset == renderOffset`, then it works
 *   as usual. If `initialOffset > renderOffset`, then we need to 'skip' some symbols from the folded ones. For that, we trim the folded
 *   range and render the skipped symbols as real ones.
 *
 * Please see [Renderer.foldLineEndIfNotFolded], [Renderer.trimFoldedRangeIfNeeded], [Renderer.renderRealTextBlocks].
 */
@ApiStatus.Experimental
internal class InlineCompletionTextRenderManager private constructor(
  editor: Editor,
  renderOffset: Int,
  private val onDispose: () -> Unit
) {

  private val renderer = Renderer(editor, renderOffset)
  private var elementsCounter = 0
  private var isActive = true

  private fun append(
    text: String,
    attributes: TextAttributes,
    initialOffset: Int, // see documentation of the upper class
    disposable: Disposable
  ): RenderedInlineCompletionElementDescriptor {
    check(isActive) { "Cannot render an element since the renderer is already disposed." }
    elementsCounter++
    disposable.whenDisposed {
      ThreadingAssertions.assertEventDispatchThread()
      elementsCounter--
      if (elementsCounter == 0) {
        cleanUp()
      }
    }
    return renderer.append(text, attributes, initialOffset)
  }

  private fun cleanUp() {
    isActive = false
    Disposer.dispose(renderer)
    onDispose()
  }

  private class Renderer(
    private val editor: Editor,
    private val renderOffset: Int,
  ) : Disposable {

    private var inlineInlay: Inlay<out InlineCompletionLineRenderer>? = null
    private val blockLineInlays = mutableListOf<Inlay<out InlineCompletionLineRenderer>>()
    private var state = RenderState.RENDERING_INLINE
    private val inlayRenderers = InlineCompletionInlayRenderer.all()
    private var foldedRange: TextRange? = null

    private val foldingManager = InlineCompletionFoldingManager.get(editor)

    fun append(text: String, attributes: TextAttributes, initialOffset: Int): RenderedInlineCompletionElementDescriptor {
      val newLines = text.lines().map { InlineCompletionRenderTextBlock(it, attributes) }
      check(newLines.isNotEmpty())
      render(newLines, initialOffset)
      return Descriptor(initialOffset)
    }

    override fun dispose() {
      editor.inlayModel.execute(true) {
        inlineInlay?.let { Disposer.dispose(it) }
        for (blockInlay in blockLineInlays) {
          Disposer.dispose(blockInlay)
        }
      }
      inlineInlay = null
      blockLineInlays.clear()
      foldedRange = null
    }

    private fun render(newLines: List<InlineCompletionRenderTextBlock>, initialOffset: Int) {
      editor.forceLeanLeft()

      removeFoldedBlocksEverywhere()
      trimFoldedRangeIfNeeded(initialOffset)

      when (state) {
        RenderState.RENDERING_INLINE -> {
          renderInline(listOf(newLines[0]))
          if (newLines.size > 1) {
            state = RenderState.RENDERING_BLOCK
            renderMultiline(newLines.subList(1, newLines.size))
          }
        }
        RenderState.RENDERING_BLOCK -> {
          renderMultiline(newLines)
        }
      }

      renderFoldedRange()
      updateDirtyInlays()
    }

    private fun renderInline(newBlocks: List<InlineCompletionRenderTextBlock>) {
      val offset = foldingManager.firstNotFoldedOffset(renderOffset)

      val inlineInlay = this.inlineInlay
      if (inlineInlay != null) {
        inlineInlay.renderer.blocks = inlineInlay.renderer.blocks + newBlocks
      }
      else {
        editor.inlayModel.execute(true) {
          val element = renderInlineInlay(editor, offset, newBlocks)
          element?.addActionAvailabilityHints(
            IdeActions.ACTION_INSERT_INLINE_COMPLETION,
            IdeActions.ACTION_INSERT_INLINE_COMPLETION_WORD,
            IdeActions.ACTION_INSERT_INLINE_COMPLETION_LINE,
            IdeActions.ACTION_NEXT_INLINE_COMPLETION_SUGGESTION,
            IdeActions.ACTION_PREV_INLINE_COMPLETION_SUGGESTION
          )
          this.inlineInlay = element
        }
      }
    }

    private fun renderMultiline(newLines: List<InlineCompletionRenderTextBlock>) {
      if (newLines.isEmpty()) {
        return
      }

      foldLineEndIfNotFolded()

      val offset = foldingManager.firstNotFoldedOffset(this.renderOffset)

      var linesToRender = newLines
      val lastInlay = blockLineInlays.lastOrNull()
      if (lastInlay != null) {
        lastInlay.renderer.blocks = lastInlay.renderer.blocks + linesToRender[0]
        linesToRender = linesToRender.subList(1, linesToRender.size)
      }

      for (newLine in linesToRender) {
        blockLineInlays += renderBlockInlay(editor, offset, listOf(newLine)) ?: break
      }
    }

    private fun renderBlockInlay(
      editor: Editor,
      offset: Int,
      blocks: List<InlineCompletionRenderTextBlock>
    ): Inlay<out InlineCompletionLineRenderer>? {
      return inlayRenderers.firstNotNullOfOrNull { it.renderBlockInlay(editor, offset, blocks) }
    }

    private fun renderInlineInlay(
      editor: Editor,
      offset: Int,
      blocks: List<InlineCompletionRenderTextBlock>
    ): Inlay<out InlineCompletionLineRenderer>? {
      return inlayRenderers.firstNotNullOfOrNull { it.renderInlineInlay(editor, offset, blocks) }
    }

    private fun trimFoldedRangeIfNeeded(initialOffset: Int) {
      val foldedRange = this.foldedRange ?: return
      if (initialOffset < foldedRange.startOffset) {
        LOG.error("Incorrect state of inline completion rendering. It is called in the wrong order.")
        return
      }
      val trimRange = TextRange(foldedRange.startOffset, initialOffset)
      this.foldedRange = TextRange(initialOffset, foldedRange.endOffset)
      renderRealTextBlocks(trimRange, areFolded = false)
    }

    private fun renderFoldedRange() {
      val foldedRange = this.foldedRange ?: return
      renderRealTextBlocks(foldedRange, areFolded = true)
    }

    private fun renderRealTextBlocks(rangeInEditor: TextRange, areFolded: Boolean) {
      if (rangeInEditor.isEmpty) {
        return
      }
      val blocks = getBlocksForRealText(editor, rangeInEditor, state == RenderState.RENDERING_INLINE)
      if (areFolded) {
        blocks.forEach { block -> block.data.putUserData(FOLDED_BLOCK_KEY, Unit) }
      }
      when (state) {
        RenderState.RENDERING_INLINE -> {
          renderInline(blocks)
        }
        RenderState.RENDERING_BLOCK -> {
          val lastBlock = blockLineInlays.last()
          lastBlock.renderer.blocks = lastBlock.renderer.blocks + blocks
        }
      }
    }

    private fun Editor.forceLeanLeft() {
      val visualPosition = caretModel.visualPosition
      if (visualPosition.leansRight) {
        val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
        caretModel.moveToVisualPosition(leftLeaningPosition)
      }
    }

    private fun foldLineEndIfNotFolded() {
      if (foldedRange != null) {
        return
      }
      val range = foldingManager.foldLineEnd(renderOffset, this) ?: return
      foldedRange = range
      if (editor.document.getText(range).contains('\n')) {
        LOG.error("Incorrect state of inline completion rendering. Folding mustn't contain a new line break.")
      }

      // Now, the inline inlay is rendered at the wrong offset. Need to re-render.
      val inlineInlay = this.inlineInlay
      if (inlineInlay != null) {
        val inlineBlocks = inlineInlay.renderer.blocks
        Disposer.dispose(inlineInlay)
        this.inlineInlay = null
        renderInline(inlineBlocks)
      }
    }

    private fun updateDirtyInlays() {
      inlineInlay?.updateIfNeeded()
      blockLineInlays.forEach { it.updateIfNeeded() }
    }

    private fun Inlay<out InlineCompletionLineRenderer>.updateIfNeeded() {
      renderer.updateIfNeeded(this@updateIfNeeded)
    }

    private fun removeFoldedBlocksEverywhere() {
      inlineInlay?.removeFoldedBlocks()
      blockLineInlays.forEach { it.removeFoldedBlocks() }
    }

    private fun Inlay<out InlineCompletionLineRenderer>.removeFoldedBlocks() {
      val blocks = renderer.blocks.filter { it.data.getUserData(FOLDED_BLOCK_KEY) == null }
      if (blocks.size != renderer.blocks.size) {
        renderer.blocks = blocks
      }
    }

    private fun Inlay<*>.addActionAvailabilityHints(vararg actionIds: String) {
      for (actionId in actionIds) {
        val hint = EditorActionAvailabilityHint(
          actionId,
          EditorActionAvailabilityHint.AvailabilityCondition.CaretOnStart,
        )
        addActionAvailabilityHint(hint)
      }
    }

    private inner class Descriptor(private val offset: Int) : RenderedInlineCompletionElementDescriptor {
      override fun getStartOffset(): Int? = offset

      override fun getEndOffset(): Int? = offset

      override fun getRectangle(): Rectangle? {
        return blockLineInlays.fold(inlineInlay?.bounds) { result, inlay ->
          val newBounds = inlay.bounds ?: return@fold result
          if (result == null) newBounds else result.union(newBounds)
        }
      }
    }

    private enum class RenderState {
      RENDERING_INLINE,
      RENDERING_BLOCK
    }
  }

  companion object {

    private val STORAGE_KEY = Key.create<Storage<Int, InlineCompletionTextRenderManager>>("inline.completion.text.render")
    private val FOLDED_BLOCK_KEY = Key.create<Unit>("inline.completion.folded.block.marker")
    private val LOG = thisLogger()

    @ApiStatus.Experimental
    @RequiresEdt
    internal fun render(
      editor: Editor,
      text: String,
      attributes: TextAttributes,
      offset: Int,
      disposable: Disposable
    ): RenderedInlineCompletionElementDescriptor {
      ThreadingAssertions.assertEventDispatchThread()

      val actualOffset = InlineCompletionFoldingManager.get(editor).offsetOfFoldStart(offset)

      val storage = editor.getUserData(STORAGE_KEY) ?: Storage()
      editor.putUserData(STORAGE_KEY, storage)

      val renderer = storage.getOrInitialize(actualOffset) {
        InlineCompletionTextRenderManager(editor, renderOffset = actualOffset) {
          storage.remove(actualOffset)
          if (storage.isEmpty()) {
            editor.removeUserData(STORAGE_KEY)
          }
        }
      }
      return renderer.append(text, attributes, offset, disposable)
    }

    private class Storage<K : Any, V : Any> {
      private val map = mutableMapOf<K, V>()

      fun getOrInitialize(key: K, init: () -> V): V = map.computeIfAbsent(key) { init() }

      fun remove(key: K) {
        checkNotNull(map.remove(key))
      }

      fun isEmpty(): Boolean = map.isEmpty()
    }
  }
}

@ApiStatus.Experimental
internal interface RenderedInlineCompletionElementDescriptor {

  fun getStartOffset(): Int?

  fun getEndOffset(): Int?

  fun getRectangle(): Rectangle?
}
