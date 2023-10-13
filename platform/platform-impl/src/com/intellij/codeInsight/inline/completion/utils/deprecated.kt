// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

// collection of deprecated functions

@Deprecated(
  "replaced with direct event call type",
  ReplaceWith("invoke(InlineCompletionEvent.DocumentChange(event, editor))"),
  DeprecationLevel.ERROR
)
@ScheduledForRemoval
fun InlineCompletionHandler.invoke(event: DocumentEvent, editor: Editor) {
  return invoke(InlineCompletionEvent.DocumentChange(event, editor))
}

@Deprecated(
  "replaced with direct event call type",
  ReplaceWith("invoke(InlineCompletionEvent.LookupChange(event))"),
  DeprecationLevel.ERROR
)
@ScheduledForRemoval
fun InlineCompletionHandler.invoke(event: LookupEvent) {
  return invoke(InlineCompletionEvent.LookupChange(event))
}

@Deprecated(
  "replaced with direct event call type",
  ReplaceWith("invoke(InlineCompletionEvent.DirectCall(editor, caret, context))"),
  DeprecationLevel.ERROR
)
@ScheduledForRemoval
fun InlineCompletionHandler.invoke(editor: Editor, file: PsiFile, caret: Caret, context: DataContext?) {
  return invoke(InlineCompletionEvent.DirectCall(editor, caret, context))
}

@Deprecated(
  "Resetting completion context is unsafe now. Use direct get/reset/remove~InlineCompletionContext instead",
  ReplaceWith("getInlineCompletionContextOrNull()"), DeprecationLevel.ERROR
)
@RequiresEdt
@ScheduledForRemoval
fun Editor.initOrGetInlineCompletionContext(): InlineCompletionContext {
  return InlineCompletionContext.getOrNull(this)!!
}

@Deprecated(
  "Use direct InlineCompletionContext.getOrNull instead",
  ReplaceWith("InlineCompletionContext.getOrNull(this)"), DeprecationLevel.ERROR
)
@RequiresEdt
@ScheduledForRemoval
fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = InlineCompletionContext.getOrNull(this)
