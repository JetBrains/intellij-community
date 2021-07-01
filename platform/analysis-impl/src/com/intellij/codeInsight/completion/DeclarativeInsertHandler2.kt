// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement

class CompositeDeclarativeInsertHandler(val handlers: Map<Char, DeclarativeInsertHandler2>, val fallbackInsertHandler: InsertHandler<LookupElement>) : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    (handlers[context.completionChar] ?: handlers[Lookup.NORMAL_SELECT_CHAR])?.handleInsert(context, item)
  }
}

// todo: merge with DeclarativeInsertHandler
// todo: filter out empty insertion operations
// todo: replace insertOperations with replaceOperations
// todo: implement IntelliJ part
data class DeclarativeInsertHandler2(
  val textOperations: List<RelativeTextEdit>,
  val offsetToPutCaret: Int,
  val addCompletionChar: Boolean = true,
  val postInsertHandler: InsertHandler<LookupElement>?
) : InsertHandler<LookupElement> {
  data class RelativeTextEdit(val rangeFrom: Int, val rangeTo: Int, val newText: String) {
    fun toAbsolute(baseOffset: Int) = AbsoluteTextEdit(rangeFrom + baseOffset, rangeTo + baseOffset, newText)
  }
  data class AbsoluteTextEdit(val rangeFrom: Int, val rangeTo: Int, val newText: String)

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val baseOffset = context.editor.caretModel.offset

    context.setAddCompletionChar(addCompletionChar)

    textOperations.sortedByDescending { (from, _, _) -> from }
      .map { it.toAbsolute(baseOffset) }
      .forEach { (from, to, newText) ->
        context.document.replaceString(from, to, newText)
      }

    context.editor.caretModel.currentCaret.run {
      val newOffset = offset + offsetToPutCaret
      moveToOffset(newOffset)
    }

    postInsertHandler?.handleInsert(context, item)
  }

  //class Builder {
  //  val textOperations = mutableListOf<RelativeTextEdit>()
  //  var offsetToPutCaret : Int = 0
  //  var addCompletionChar: Boolean = true
  //  var postInsertHandler: InsertHandler<LookupElement>? = null
  //
  //  fun build(): DeclarativeInsertHandler2 {
  //
  //  }
  //}
}