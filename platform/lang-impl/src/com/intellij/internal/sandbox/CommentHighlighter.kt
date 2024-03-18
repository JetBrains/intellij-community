// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.sandbox

import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor

// client: highlight comments with "xxx" text inside
class CommentHighlighter : Highlighter {
  override fun createVisitor(session: HighlightSession): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitComment(comment: PsiComment) { // no imperative PSI traversing, to allow caching/concurrency/reduce scope optimizations
        if (comment.text.contains("xxx")) {
          session.sink.newHighlight { // no explicit result, to avoid leaks/messing with internals
            range(comment.textRange)
            description("xxx comment")

            fix { // can be computed later, depending on HighlightSession options?
              action(EmptyIntentionAction("remove"))
              fixRange(comment.textRange.shiftLeft(2))
            }
          }
        }
      }
    }
  }
}

