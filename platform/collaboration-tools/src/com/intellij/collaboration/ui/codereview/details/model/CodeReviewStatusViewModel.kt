// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.data.CodeReviewCIJob
import kotlinx.coroutines.flow.Flow

interface CodeReviewStatusViewModel {
  val hasConflicts: Flow<Boolean>
  val ciJobs: Flow<List<CodeReviewCIJob>>
}