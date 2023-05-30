// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CodeReviewBranchesViewModel {
  val targetBranch: StateFlow<String>
  val sourceBranch: StateFlow<String>
  val isCheckedOut: Flow<Boolean>
}