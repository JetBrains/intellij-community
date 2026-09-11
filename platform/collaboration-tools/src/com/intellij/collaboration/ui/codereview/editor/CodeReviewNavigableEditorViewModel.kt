// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CodeReviewNavigableEditorViewModel {
  val canNavigate: Boolean

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun canGotoNextComment(threadId: String): Boolean
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun canGotoNextComment(line: Int): Boolean

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun canGotoPreviousComment(threadId: String): Boolean
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun canGotoPreviousComment(line: Int): Boolean

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun gotoNextComment(threadId: String)
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun gotoNextComment(line: Int)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun gotoPreviousComment(threadId: String)
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun gotoPreviousComment(line: Int)

  companion object {
    val KEY: Key<CodeReviewNavigableEditorViewModel> = Key.create("CodeReview.Navigable.Editor.ViewModel")
  }
}