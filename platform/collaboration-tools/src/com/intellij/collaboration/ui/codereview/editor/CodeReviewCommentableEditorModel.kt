// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.LineRange
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt

interface CodeReviewCommentableEditorModel {
  @RequiresEdt
  fun canCreateComment(lineIdx: Int): Boolean

  @RequiresEdt
  fun requestNewComment(lineIdx: Int)

  @RequiresEdt
  fun cancelNewComment(lineIdx: Int) {}

  interface WithMultilineComments : CodeReviewCommentableEditorModel {
    @RequiresEdt
    fun canCreateComment(lineRange: LineRange): Boolean

    @RequiresEdt
    fun requestNewComment(lineRange: LineRange)
  }

  companion object {
    val KEY: Key<CodeReviewEditorGutterControlsModel> = Key.create(CodeReviewCommentableEditorModel::class.java.name)
  }
}