// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.CommonBundle
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.swingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

interface CodeReviewTextEditingViewModel : CodeReviewSubmittableTextViewModel {
  /**
   * Submit the new text in background
   */
  fun save()

  fun stopEditing()
}

fun CoroutineScope.createEditActionsConfig(editVm: CodeReviewTextEditingViewModel, afterSave: () -> Unit = {})
  : CommentInputActionsComponentFactory.Config =
  CommentInputActionsComponentFactory.Config(
    primaryAction = MutableStateFlow(editVm.submitActionIn(this, CollaborationToolsBundle.message("review.comment.save")) {
      save()
      afterSave()
    }),
    cancelAction = MutableStateFlow(swingAction(CommonBundle.getCancelButtonText()) {
      editVm.stopEditing()
    }),
    submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comment.save.hint",
                                                                   CommentInputActionsComponentFactory.submitShortcutText))
  )