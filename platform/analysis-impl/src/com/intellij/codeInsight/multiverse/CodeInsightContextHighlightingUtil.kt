// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("CodeInsightContextHighlightingUtil")

package com.intellij.codeInsight.multiverse

import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun RangeHighlighter.installCodeInsightContext(project: Project, context: CodeInsightContext) {
  if (isSharedSourceSupportEnabled(project)) {
    putUserData(highlighterContextKey, context)
  }
}

/**
 * @return the context associated with the range highlighter or null if it is not associated
 */
val RangeHighlighter.codeInsightContext: CodeInsightContext?
  @ApiStatus.Experimental
  get() {
    return getUserData(highlighterContextKey)
  }

private val highlighterContextKey = Key.create<CodeInsightContext>("highlighterContextKey")

@ApiStatus.Experimental
fun CodeInsightContext.acceptRangeHighlighter(rangeHighlighter: RangeHighlighter): Boolean {
  if (this === anyContext()) {
    return true
  }
  val rangeHighlighterContext = rangeHighlighter.codeInsightContext ?: run {
    // no context => always show
    return true
  }

  return rangeHighlighterContext == this
}