// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import kotlinx.coroutines.flow.SharedFlow

interface CodeReviewStatusViewModel {
  /**
   * Whether there are conflicts that need to be resolved before merging.
   *
   * If the value is `null`, there is a check currently in progress or there is something
   * else preventing us from knowing whether there are conflicts.
   */
  val hasConflicts: SharedFlow<Boolean?>

  /**
   * A flow or conversation resolution check
   *
   * IMPORTANT: meaning is flipped due to a naming typo
   * false if every required conversation was resolved, true otherwise
   */
  val requiredConversationsResolved: SharedFlow<Boolean>

  val ciJobs: SharedFlow<List<CodeReviewCIJob>>

  val showJobsDetailsRequests: SharedFlow<List<CodeReviewCIJob>>

  fun showJobsDetails()
}