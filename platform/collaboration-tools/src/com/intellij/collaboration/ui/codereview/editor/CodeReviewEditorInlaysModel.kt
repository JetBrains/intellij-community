// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * A UI model for an editor with code review inlays
 * This model should exist in the same scope as the gutter
 * One model - one gutter
 */
@ApiStatus.Internal
interface CodeReviewEditorInlaysModel<I : CodeReviewInlayModel> {
  val inlays: StateFlow<Collection<I>>
}

@ApiStatus.Internal
interface CodeReviewInlayModel : EditorMapped {
  val key: Any
  override val line: StateFlow<Int?>
  override val isVisible: StateFlow<Boolean>
}