// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class CompositeDeclarativeInsertHandler(val handlers: Map<Char, Lazy<DeclarativeInsertHandler2>>,
                                             val fallbackInsertHandler: InsertHandler<LookupElement>?)
  : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    (handlers[context.completionChar]?.value ?: fallbackInsertHandler)?.handleInsert(context, item)
  }

  companion object {
    fun withUniversalHandler(completionChars: CharArray,
                             handler: Lazy<DeclarativeInsertHandler2>): CompositeDeclarativeInsertHandler {
      val handlersMap = completionChars.associate { it to handler }
      // it's important not to provide a fallbackInsertHandler
      return CompositeDeclarativeInsertHandler(handlersMap, null)
    }

    fun withUniversalHandler(completionChars: CharArray,
                             handler: DeclarativeInsertHandler2): CompositeDeclarativeInsertHandler {
      val lazyHandler = lazy { handler }
      return withUniversalHandler(completionChars, lazyHandler)
    }
  }
}

/**
 * Important statements of the contract.
 * * Operations of RelativeTextEdit may NOT intersect, their offsets must also be independent (with regards to order of application), for example:
 *    operations (0, 0, "AA) and (2, 2, "BB) - offsets of "BB" should not be calculated with expectation of operation "AA" applied first, and vice-versa.
 *
 *  * offsetToPutCaret - should be calculated under assumption that all operations already applied.
 */
@ApiStatus.Experimental
open class DeclarativeInsertHandler2 protected constructor(
  val textOperations: List<RelativeTextEdit>,
  val offsetToPutCaret: Int,
  val addCompletionChar: Boolean,
  val postInsertHandler: InsertHandler<LookupElement>?,
  val popupOptions: PopupOptions
) : InsertHandler<LookupElement> {
  data class RelativeTextEdit(val rangeFrom: Int, val rangeTo: Int, val newText: String) {
    fun toAbsolute(baseOffset: Int) = AbsoluteTextEdit(rangeFrom + baseOffset, rangeTo + baseOffset, newText)
  }

  data class AbsoluteTextEdit(val rangeFrom: Int, val rangeTo: Int, val newText: String)

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    conditionalHandleInsert(context, item, applyTextOperations = true)
  }

  protected fun conditionalHandleInsert(context: InsertionContext, item: LookupElement, applyTextOperations: Boolean) {
    val baseOffset = context.editor.caretModel.offset

    context.setAddCompletionChar(addCompletionChar)

    if (applyTextOperations) {
      textOperations.sortedByDescending { (from, _, _) -> from }
        .map { it.toAbsolute(baseOffset) }
        .forEach { (from, to, newText) ->
          context.document.replaceString(from, to, newText)
        }
    }

    context.editor.caretModel.currentCaret.run {
      val newOffset = offset + offsetToPutCaret
      moveToOffset(newOffset)
    }

    postInsertHandler?.handleInsert(context, item)

    if (popupOptions.showPopup()) {
      val element = context.file.findElementAt(context.startOffset)
      val popupController = AutoPopupController.getInstance(context.project)

      if (element != null && popupController != null) {
        when (popupOptions) {
          PopupOptions.ParameterInfo -> popupController.autoPopupParameterInfo(context.editor, element)
          PopupOptions.MemberLookup -> popupController.autoPopupMemberLookup(context.editor, null)
          PopupOptions.DoNotShow -> Unit
        }
      }
    }
  }

  sealed class PopupOptions {
    object DoNotShow : PopupOptions()
    object ParameterInfo : PopupOptions()
    object MemberLookup : PopupOptions()

    fun showPopup() = when (this) {
      is DoNotShow -> false
      else -> true
    }
  }

  // This is a handy interface for Java interop
  fun interface HandlerProducer {
    fun produce(builder: Builder)
  }
  class LazyBuilder(private val block: HandlerProducer): Lazy<DeclarativeInsertHandler2> {
    private val delegate = lazy { Builder().also(block::produce).build() }

    override val value: DeclarativeInsertHandler2
      get() = delegate.value

    override fun isInitialized(): Boolean = delegate.isInitialized()
  }

  class Builder {
    private val textOperations = mutableListOf<RelativeTextEdit>()
    var offsetToPutCaret: Int = 0
    private var addCompletionChar: Boolean = false
    private var postInsertHandler: InsertHandler<LookupElement>? = null
    private var popupOptions: PopupOptions = PopupOptions.DoNotShow

    fun addOperation(offsetAt: Int, newText: String) = addOperation(offsetAt, offsetAt, newText)
    fun addOperation(offsetFrom: Int, offsetTo: Int, newText: String) = addOperation(RelativeTextEdit(offsetFrom, offsetTo, newText))
    fun addOperation(operation: RelativeTextEdit): Builder {
      val operationIsEmpty = (operation.rangeFrom == operation.rangeTo) && operation.newText.isEmpty()
      if (!operationIsEmpty)
        textOperations.add(operation)

      return this
    }

    fun withPopupOptions(newOptions: PopupOptions): Builder {
      popupOptions = newOptions
      return this
    }

    fun withOffsetToPutCaret(newOffset: Int): Builder {
      offsetToPutCaret = newOffset
      return this
    }

    fun withAddCompletionCharFlag(newAddCompletionChar: Boolean): Builder {
      addCompletionChar = newAddCompletionChar
      return this
    }

    fun withPostInsertHandler(newHandler: InsertHandler<LookupElement>): Builder {
      postInsertHandler = newHandler
      return this
    }

    fun build(): DeclarativeInsertHandler2 {
      return DeclarativeInsertHandler2(textOperations, offsetToPutCaret, addCompletionChar, postInsertHandler, popupOptions)
    }
  }
}

@ApiStatus.Experimental
class SingleInsertionDeclarativeInsertHandler(private val stringToInsert: String,
                                              popupOptions: PopupOptions)
  : DeclarativeInsertHandler2(listOf(RelativeTextEdit(0, 0, stringToInsert)),
                              stringToInsert.length,
                              false,
                              null,
                              popupOptions) {

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val applyTextOperations = !isValueAlreadyHere(context.editor)
    conditionalHandleInsert(context, item, applyTextOperations)
  }

  private fun isValueAlreadyHere(editor: Editor): Boolean {
    val startOffset = editor.caretModel.offset
    val valueLength = stringToInsert.length
    return editor.document.textLength >= startOffset + valueLength &&
           editor.document.getText(TextRange.create(startOffset, startOffset + valueLength)) == stringToInsert
  }
}