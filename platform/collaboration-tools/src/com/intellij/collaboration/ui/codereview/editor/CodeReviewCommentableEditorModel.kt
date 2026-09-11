// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.LineRange
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt

interface CodeReviewCommentableEditorModel {
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun canCreateComment(lineIdx: Int): Boolean

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun requestNewComment(lineIdx: Int)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun cancelNewComment(lineIdx: Int) {}

  interface WithMultilineComments : CodeReviewCommentableEditorModel {
    @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
    fun canCreateComment(lineRange: LineRange): Boolean

    @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
    fun requestNewComment(lineRange: LineRange)
  }

  companion object {
    val KEY: Key<CodeReviewEditorGutterControlsModel> = Key.create(CodeReviewCommentableEditorModel::class.java.name)
  }
}