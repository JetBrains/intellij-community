// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class InlineCompletionContext internal constructor(val editor: Editor) {
  private val prefixUpdater = PrefixUpdater()

  private val state = InlineState()

  val isCurrentlyDisplayingInlays: Boolean
    get() = state.elements.any { !it.isEmpty }

  val startOffset: Int?
    get() = state.firstElement?.offset

  val lastOffset: Int?
    get() = state.lastElement?.offset

  val lineToInsert: String
    get() = prefixUpdater.lineToInsert

  val elements: List<InlineCompletionElement>
    get() = prefixUpdater.getElements()

  fun addElement(element: InlineCompletionElement) {
    prefixUpdater.addElement(element)
    state.addElement(element)
  }

  fun clear() {
    state.clear()
    prefixUpdater.clear()
  }

  @RequiresBlockingContext
  fun appendPrefix(fragment: CharSequence, offset: Int): PrefixUpdateResult {
    val prefixUpdateResult = prefixUpdater.appendPrefix(fragment)
    if (prefixUpdateResult == PrefixUpdateResult.SUCCESS) {
      // TODO this code should be in InlineCompletionHandler
      application.invokeAndWait {
        editor.inlayModel.execute(true) {
          state.clear()
          elements.forEach {
            it.render(editor, lastOffset ?: offset)
            state.addElement(it)
          }
        }
      }
    }
    return prefixUpdateResult
  }

  enum class PrefixUpdateResult {
    SUCCESS,
    FAIL
  }

  companion object {
    private val LOG = thisLogger()
    private val INLINE_COMPLETION_CONTEXT = Key.create<InlineCompletionContext>("inline.completion.context")

    fun getOrNull(editor: Editor): InlineCompletionContext? = editor.getUserData(INLINE_COMPLETION_CONTEXT)
    internal fun getOrInit(editor: Editor): InlineCompletionContext {
      return editor.getUserData(INLINE_COMPLETION_CONTEXT) ?: InlineCompletionContext(editor).also {
        editor.putUserData(INLINE_COMPLETION_CONTEXT, it)
      }
    }

    internal fun remove(editor: Editor) {
      getOrNull(editor)?.clear()

      editor.putUserData(INLINE_COMPLETION_CONTEXT, null)
        .also { LOG.trace("Remove inline completion context") }
    }

    @Deprecated(
      "Resetting completion context is unsafe now. Use direct get/reset/remove~InlineCompletionContext instead",
      ReplaceWith("getInlineCompletionContextOrNull()"), DeprecationLevel.ERROR
    )
    fun Editor.initOrGetInlineCompletionContext(): InlineCompletionContext {
      return getOrNull(this)!!
    }

    @Deprecated(
      "Use direct InlineCompletionContext.getOrNull instead",
      ReplaceWith("InlineCompletionContext.getOrNull(this)"), DeprecationLevel.ERROR
    )
    fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = getOrNull(this)
  }
}

private class PrefixUpdater {

  private var typedPrefixLength = 0
  private val elements = mutableListOf<InlineCompletionElement>()

  private val fullText: String
    get() = elements.joinToString("") { it.text }

  val lineToInsert: String
    get() = fullText.drop(typedPrefixLength)

  fun getElements(): List<InlineCompletionElement> {
    if (elements.isEmpty()) {
      return emptyList()
    }

    var currentPrefixLength = typedPrefixLength
    val currentElementIndex = elements.indexOfFirst {
      currentPrefixLength -= it.text.length
      currentPrefixLength < 0
    }
    val currentElement = elements[currentElementIndex]
    currentPrefixLength += currentElement.text.length
    return buildList {
      add(InlineCompletionElement(currentElement.text.drop(currentPrefixLength)))
      for (index in currentElementIndex + 1 until elements.size) {
        add(elements[index])
      }
    }
  }

  fun addElement(element: InlineCompletionElement) {
    elements += element
  }

  fun appendPrefix(fragment: CharSequence): InlineCompletionContext.PrefixUpdateResult {
    return if (fragment.isNotEmpty() && lineToInsert.startsWith(fragment)) {
      typedPrefixLength += fragment.length
      if (typedPrefixLength >= fullText.length) {
        InlineCompletionContext.PrefixUpdateResult.FAIL
      }
      else {
        InlineCompletionContext.PrefixUpdateResult.SUCCESS
      }
    }
    else {
      InlineCompletionContext.PrefixUpdateResult.FAIL
    }
  }

  fun clear() {
    elements.clear()
    typedPrefixLength = 0
  }
}
