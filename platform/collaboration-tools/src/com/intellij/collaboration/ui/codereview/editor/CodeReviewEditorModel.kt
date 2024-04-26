// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import org.jetbrains.annotations.ApiStatus

/**
 * A UI model for an editor with code review inlays and controls
 * This model should exist in the same scope as the gutter
 * One model - one gutter
 */
@ApiStatus.Internal
interface CodeReviewEditorModel<I : CodeReviewInlayModel>
  : CodeReviewEditorInlaysModel<I>, CodeReviewEditorGutterControlsModel