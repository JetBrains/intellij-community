// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

/**
 * Accumulates all the text to be rendered at one offset and renders them.
 * To be able to render streaming text, all blocks are split line by line.
 *
 * When a new text part comes, the last line is changed, while the previous lines stay the same.
 * Even if an element has multiline text, it's split into lines and they are rendered one by one.
 *
 * Rules:
 * * Disposing of the whole text happens only when all the 'children' elements are disposed.
 *   It relies on the internal implementation of the inline completion: all elements are always disposed at once.
 * * Each 'child' element returns [Rectangle] that represents the whole text. It may be fixed at some point.
 */
@ApiStatus.Experimental
internal class InlineCompletionTextRenderManager private constructor(
  editor: Editor,
  offset: Int,
  private val onDispose: () -> Unit
) {

  private val renderer = Renderer(editor, offset)
  private var elementsCounter = 0
  private var isActive = true

  private fun append(text: String, attributes: TextAttributes, disposable: Disposable): RenderedInlineCompletionElementDescriptor {
    check(isActive) { "Cannot render an element since the renderer is already disposed." }
    elementsCounter++
    disposable.whenDisposed {
      ThreadingAssertions.assertEventDispatchThread()
      elementsCounter--
      if (elementsCounter == 0) {
        cleanUp()
      }
    }
    return renderer.append(text, attributes)
  }

  private fun cleanUp() {
    isActive = false
    Disposer.dispose(renderer)
    onDispose()
  }

  private class Renderer(private val editor: Editor, private val offset: Int) : Disposable {

    private var suffixInlay: Inlay<out InlineCompletionLineRenderer>? = null
    private val blockLineInlays = mutableListOf<Inlay<out InlineCompletionLineRenderer>>()
    private var state = RenderState.RENDERING_SUFFIX
    private val inlayRenderers = InlineCompletionInlayRenderer.all()
    private val descriptor = Descriptor()

    fun append(text: String, attributes: TextAttributes): RenderedInlineCompletionElementDescriptor {
      val newLines = text.lines().map { InlineCompletionRenderTextBlock(it, attributes) }
      check(newLines.isNotEmpty())
      render(newLines)
      return descriptor
    }

    override fun dispose() {
      editor.inlayModel.execute(true) {
        suffixInlay?.let { Disposer.dispose(it) }
        for (blockInlay in blockLineInlays) {
          Disposer.dispose(blockInlay)
        }
      }
      suffixInlay = null
      blockLineInlays.clear()
    }

    private fun render(newLines: List<InlineCompletionRenderTextBlock>) {
      editor.forceLeanLeft()
      val newBlocksAfterSuffix = renderSuffix(newLines)
      renderMultiline(newBlocksAfterSuffix)
    }

    private fun renderSuffix(newLines: List<InlineCompletionRenderTextBlock>): List<InlineCompletionRenderTextBlock> {
      if (state != RenderState.RENDERING_SUFFIX) {
        return newLines
      }

      val previousSuffix = suffixInlay?.renderer?.blocks ?: emptyList()
      val suffixBlocks = previousSuffix + newLines[0]

      suffixInlay?.let { Disposer.dispose(it) }
      suffixInlay = null

      editor.inlayModel.execute(true) {
        val element = renderInlineInlay(editor, offset, suffixBlocks)
        element?.addActionAvailabilityHints(
          IdeActions.ACTION_INSERT_INLINE_COMPLETION,
          IdeActions.ACTION_INSERT_INLINE_COMPLETION_WORD,
          IdeActions.ACTION_INSERT_INLINE_COMPLETION_LINE,
          IdeActions.ACTION_NEXT_INLINE_COMPLETION_SUGGESTION,
          IdeActions.ACTION_PREV_INLINE_COMPLETION_SUGGESTION
        )
        suffixInlay = element
      }

      return newLines.subList(1, newLines.size)
    }

    private fun renderMultiline(newLines: List<InlineCompletionRenderTextBlock>) {
      if (newLines.isEmpty()) {
        return
      }
      if (state == RenderState.RENDERING_SUFFIX) {
        state = RenderState.RENDERING_BLOCK
      }

      var linesToRender = newLines
      val lastInlay = blockLineInlays.lastOrNull()
      if (lastInlay != null) {
        val lastLineBlocks = lastInlay.renderer.blocks + linesToRender[0]
        Disposer.dispose(lastInlay)
        val newInlay = renderBlockInlay(editor, offset, lastLineBlocks) ?: return
        blockLineInlays[blockLineInlays.lastIndex] = newInlay
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

    private fun Editor.forceLeanLeft() {
      val visualPosition = caretModel.visualPosition
      if (visualPosition.leansRight) {
        val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
        caretModel.moveToVisualPosition(leftLeaningPosition)
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

    private inner class Descriptor : RenderedInlineCompletionElementDescriptor {
      override fun getStartOffset(): Int? = suffixInlay?.offset

      override fun getEndOffset(): Int? = suffixInlay?.offset

      override fun getRectangle(): Rectangle? {
        return blockLineInlays.fold(suffixInlay?.bounds) { result, inlay ->
          val newBounds = inlay.bounds ?: return@fold result
          if (result == null) newBounds else result.union(newBounds)
        }
      }
    }

    private enum class RenderState {
      RENDERING_SUFFIX,
      RENDERING_BLOCK
    }
  }

  companion object {

    private val STORAGE_KEY = Key.create<Storage<Int, InlineCompletionTextRenderManager>>("inline.completion.text.render")

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

      val storage = editor.getUserData(STORAGE_KEY) ?: Storage()
      editor.putUserData(STORAGE_KEY, storage)

      val renderer = storage.getOrInitialize(offset) {
        InlineCompletionTextRenderManager(editor, offset) {
          storage.remove(offset)
          if (storage.isEmpty()) {
            editor.removeUserData(STORAGE_KEY)
          }
        }
      }
      return renderer.append(text, attributes, disposable)
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
