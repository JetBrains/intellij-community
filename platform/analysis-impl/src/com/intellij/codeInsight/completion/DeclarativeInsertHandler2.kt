// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement

class CompositeDeclarativeInsertHandler(val handlers: Map<Char, DeclarativeInsertHandler2>) : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    (handlers[context.completionChar] ?: handlers[Lookup.NORMAL_SELECT_CHAR])?.handleInsert(context, item)
  }
}

// todo: merge with DeclarativeInsertHandler
// todo: filter out empty insertion operations
// todo: replace insertOperations with replaceOperations
// todo: implement IntelliJ part
class DeclarativeInsertHandler2(
  val insertOperations: List<Pair<Int, String>>,
  val offsetToPutCaret: Int,
  val addCompletionChar: Boolean = true,
  val postInsertHandler: InsertHandler<LookupElement>?
) : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    context.setAddCompletionChar(addCompletionChar)

    insertOperations.sortedByDescending { it.first }.forEach {
      context.document.insertString(it.first, it.second)
    }

    postInsertHandler?.handleInsert(context, item)
  }
}