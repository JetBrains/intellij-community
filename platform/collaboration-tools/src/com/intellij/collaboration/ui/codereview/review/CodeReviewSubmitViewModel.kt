// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.review

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A view model for a review submission interface
 */
interface CodeReviewSubmitViewModel {
  /**
   * The number of comments that will be submitted in a review
   */
  val draftCommentsCount: StateFlow<Int>

  /**
   * Body of the review comment
   */
  val text: MutableStateFlow<String>

  /**
   * Review submission in progress
   */
  val isBusy: StateFlow<Boolean>

  /**
   * Review submission error
   */
  val error: StateFlow<Throwable?>

  /**
   * Cancel the submission
   */
  fun cancel()
}