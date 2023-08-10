// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface CodeReviewStatusViewModel {
  val hasConflicts: SharedFlow<Boolean>
  val ciJobs: SharedFlow<List<CodeReviewCIJob>>

  val showJobsDetailsRequests: SharedFlow<List<CodeReviewCIJob>>

  fun showJobsDetails()
}