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
import java.awt.Rectangle

// TODO docs
@ApiStatus.Experimental
class InlineCompletionGrayTextElementRenderer private constructor(
  editor: Editor,
  offset: Int,
  private val onDispose: () -> Unit
) {

  private val renderer = Renderer(editor, offset)
  private var elementsCounter = 0
  private var isActive = true

  private fun append(text: String, disposable: Disposable): Rectangle? {
    check(isActive) { "Cannot render an element since the renderer is already disposed." }
    disposable.whenDisposed {
      // TODO exclude [text] from rendering
      ThreadingAssertions.assertEventDispatchThread()
      elementsCounter--
      if (elementsCounter == 0) {
        cleanUp()
      }
    }
    elementsCounter++
    return renderer.append(text)
  }

  private fun cleanUp() {
    isActive = false
    Disposer.dispose(renderer)
    onDispose()
  }

  private class Renderer(private val editor: Editor, private val offset: Int) : Disposable {

    private var suffixInlay: Inlay<*>? = null
    private var blockInlay: Inlay<*>? = null
    private val currentLines = mutableListOf<String>()

    fun append(text: String): Rectangle? {
      if (text.isEmpty()) {
        return null
      }
      val lines = text.lines()
      if (currentLines.isEmpty()) {
        currentLines.add("")
      }
      currentLines[currentLines.size - 1] += lines.first()
      if (lines.size > 1) {
        currentLines += lines.subList(1, lines.size)
      }
      return render()
    }

    override fun dispose() {
      reset()
      currentLines.clear()
    }

    private fun render(): Rectangle? {
      editor.forceLeanLeft()
      editor.inlayModel.execute(true) {
        reset()
        renderSuffix()
        renderBlock()
      }
      return getRectangle()
    }

    private fun renderSuffix() {
      if (currentLines.isEmpty() || currentLines.first().isEmpty()) {
        return
      }
      val suffix = currentLines.first()
      val element = editor.inlayModel.addInlineElement(offset, true, InlineSuffixRenderer(editor, suffix))
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

    private fun renderBlock() {
      if (currentLines.size <= 1) {
        return
      }
      val lines = currentLines.subList(1, currentLines.size)
      blockInlay = editor.inlayModel.addBlockElement(
        offset,
        true,
        false,
        1,
        InlineBlockElementRenderer(editor, lines)
      )
    }

    private fun getRectangle(): Rectangle? {
      val bounds = suffixInlay?.bounds?.let { Rectangle(it) }
      blockInlay?.bounds?.let { bounds?.add(Rectangle(it)) }
      return bounds
    }

    private fun reset() {
      suffixInlay?.let { Disposer.dispose(it) }
      blockInlay?.let { Disposer.dispose(it) }
      suffixInlay = null
      blockInlay = null
    }

    private fun Editor.forceLeanLeft() {
      val visualPosition = caretModel.visualPosition
      if (visualPosition.leansRight) {
        val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
        caretModel.moveToVisualPosition(leftLeaningPosition)
      }
    }
  }

  companion object {

    private val STORAGE_KEY = Key.create<Storage<Int, InlineCompletionGrayTextElementRenderer>>("inline.completion.gray.text.render")

    @ApiStatus.Experimental
    @RequiresEdt
    fun render(editor: Editor, text: String, offset: Int, disposable: Disposable): Rectangle? {
      ThreadingAssertions.assertEventDispatchThread()

      val storage = editor.getUserData(STORAGE_KEY) ?: Storage()
      editor.putUserData(STORAGE_KEY, storage)

      val renderer = storage.getOrInitialize(offset) {
        InlineCompletionGrayTextElementRenderer(editor, offset) {
          storage.remove(offset)
          if (storage.isEmpty()) {
            editor.removeUserData(STORAGE_KEY)
          }
        }
      }
      return renderer.append(text, disposable)
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
