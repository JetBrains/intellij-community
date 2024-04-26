// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.sandbox

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

// main entry point
interface Highlighter {
  fun createVisitor(session: HighlightSession) : PsiElementVisitor = PsiElementVisitor.EMPTY_VISITOR
  fun highlightingPass(session: HighlightSession) = Unit
}

data class HighlightSession(
  val sink: HighlightSink,
  val psiFile: PsiFile,
  val visibleRange: Segment
  //...
)

interface HighlightSink {
  fun newHighlight(builder: HighlightBuilder.() -> Unit): HighlightBuilder
}

interface HighlightBuilder {
  fun range(range: Segment): HighlightBuilder
  fun description(text: String): HighlightBuilder
  //...

  fun fix(builder: FixBuilder.() -> Unit)
  //...
}

interface FixBuilder {
  fun action(a: IntentionAction): FixBuilder
  fun fixRange(range: Segment): FixBuilder
  //...
}
