// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CodeReviewNavigableEditorViewModel {
  @RequiresEdt
  fun canGotoNextComment(focusedThreadId: String): Boolean
  @RequiresEdt
  fun canGotoNextComment(line: Int): Boolean

  @RequiresEdt
  fun canGotoPreviousComment(focusedThreadId: String): Boolean
  @RequiresEdt
  fun canGotoPreviousComment(line: Int): Boolean

  @RequiresEdt
  fun gotoNextComment(focusedThreadId: String)
  @RequiresEdt
  fun gotoNextComment(line: Int)

  @RequiresEdt
  fun gotoPreviousComment(focusedThreadId: String)
  @RequiresEdt
  fun gotoPreviousComment(line: Int)

  companion object {
    val KEY: Key<CodeReviewNavigableEditorViewModel> = Key.create("CodeReview.Navigable.Editor.ViewModel")
  }
}