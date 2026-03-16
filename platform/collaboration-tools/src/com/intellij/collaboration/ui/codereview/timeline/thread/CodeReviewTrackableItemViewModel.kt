// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.thread

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CodeReviewTrackableItemViewModel {
  /**
   * Identifier used to track the comment inside the IDE.
   * This does not necessary match the actual GitLab/GitHub ID.
   * Do not use this for API calls.
   */
  val trackingId: String

  companion object {
    val TRACKABLE_ITEM_KEY: DataKey<CodeReviewTrackableItemViewModel> = DataKey.create("CodeReview.TrackableItem")
  }
}