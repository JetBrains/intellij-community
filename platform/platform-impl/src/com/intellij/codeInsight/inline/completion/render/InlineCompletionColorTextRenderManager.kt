// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Rectangle

// TODO docs
// TODO describe disposing (all at once)
// TODO describe rectangle
@ApiStatus.Experimental
internal class InlineCompletionColorTextRenderManager private constructor(
  editor: Editor,
  offset: Int,
  private val onDispose: () -> Unit
) {

  private val renderer = Renderer(editor, offset)
  private var elementsCounter = 0
  private var isActive = true

  private fun append(text: String, color: Color, disposable: Disposable): RenderedInlineCompletionElementDescriptor? {
    check(isActive) { "Cannot render an element since the renderer is already disposed." }
    elementsCounter++
    disposable.whenDisposed {
      ThreadingAssertions.assertEventDispatchThread()
      elementsCounter--
      if (elementsCounter == 0) {
        cleanUp()
      }
    }
    return renderer.append(text, color)
  }

  private fun cleanUp() {
    isActive = false
    Disposer.dispose(renderer)
    onDispose()
  }

  private class Renderer(private val editor: Editor, private val offset: Int) : Disposable {

    private var suffixInlay: Inlay<InlineSuffixRenderer>? = null
    private val blockLineInlays = mutableListOf<Inlay<InlineSuffixRenderer>>()
    private var state = RenderState.RENDERING_SUFFIX

    fun append(text: String, color: Color): RenderedInlineCompletionElementDescriptor? {
      if (text.isEmpty()) {
        return null
      }
      val newLines = text.lines().map { InlineCompletionRenderTextBlock(it, color) }
      render(newLines)
      return getDescriptor(offset)
    }

    override fun dispose() {
      suffixInlay?.let { Disposer.dispose(it) }
      suffixInlay = null
      for (blockInlay in blockLineInlays) {
        Disposer.dispose(blockInlay)
      }
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
      check(newLines.isNotEmpty())

      val previousSuffix = suffixInlay?.renderer?.contents ?: emptyList()
      val suffixBlocks = previousSuffix + newLines[0]

      suffixInlay?.let { Disposer.dispose(it) }
      suffixInlay = null

      if (suffixBlocks.any { it.text.isNotEmpty() }) {
        val element = editor.inlayModel.addInlineElement(offset, true, InlineSuffixRenderer(editor, suffixBlocks))
        if (element != null) {
          element.addActionAvailabilityHint(
            EditorActionAvailabilityHint(
              IdeActions.ACTION_INSERT_INLINE_COMPLETION,
              EditorActionAvailabilityHint.AvailabilityCondition.CaretOnStart,
            )
          )
          suffixInlay = element
        }
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
        val lastLineBlocks = lastInlay.renderer.contents + linesToRender[0]
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
    ): Inlay<InlineSuffixRenderer>? {
      return editor.inlayModel.addBlockElement(
        offset,
        true,
        false,
        1,
        InlineSuffixRenderer(editor, blocks)
      )
    }

    private fun Editor.forceLeanLeft() {
      val visualPosition = caretModel.visualPosition
      if (visualPosition.leansRight) {
        val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
        caretModel.moveToVisualPosition(leftLeaningPosition)
      }
    }

    private fun getDescriptor(offset: Int): RenderedInlineCompletionElementDescriptor {
      return object : RenderedInlineCompletionElementDescriptor {
        override fun getStartOffset(): Int = offset
        override fun getEndOffset(): Int = offset
        override fun getRectangle(): Rectangle? {
          return blockLineInlays.fold(suffixInlay?.bounds) { result, inlay ->
            val newBounds = inlay.bounds ?: return@fold result
            if (result == null) newBounds else result.union(newBounds)
          }
        }
      }
    }

    private enum class RenderState {
      RENDERING_SUFFIX,
      RENDERING_BLOCK
    }
  }

  companion object {

    private val STORAGE_KEY = Key.create<Storage<Int, InlineCompletionColorTextRenderManager>>("inline.completion.text.render")

    @ApiStatus.Experimental
    @RequiresEdt
    internal fun render(
      editor: Editor,
      text: String,
      color: Color,
      offset: Int,
      disposable: Disposable
    ): RenderedInlineCompletionElementDescriptor? {
      ThreadingAssertions.assertEventDispatchThread()

      val storage = editor.getUserData(STORAGE_KEY) ?: Storage()
      editor.putUserData(STORAGE_KEY, storage)

      val renderer = storage.getOrInitialize(offset) {
        InlineCompletionColorTextRenderManager(editor, offset) {
          storage.remove(offset)
          if (storage.isEmpty()) {
            editor.removeUserData(STORAGE_KEY)
          }
        }
      }
      return renderer.append(text, color, disposable)
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

  fun getStartOffset(): Int

  fun getEndOffset(): Int

  fun getRectangle(): Rectangle?
}
