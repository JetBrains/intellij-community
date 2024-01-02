// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface CodeReviewBranchesViewModel {
  val sourceBranch: StateFlow<String>
  val isCheckedOut: SharedFlow<Boolean>

  val showBranchesRequests: SharedFlow<CodeReviewBranches>

  fun fetchAndCheckoutRemoteBranch()

  val canShowInLog: Boolean get() = false
  fun fetchAndShowInLog() {}

  fun showBranches()
}

data class CodeReviewBranches(val source: String, val target: String)