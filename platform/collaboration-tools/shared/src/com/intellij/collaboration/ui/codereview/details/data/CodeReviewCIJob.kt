// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.data

import com.intellij.openapi.util.NlsSafe

data class CodeReviewCIJob(
  val name: @NlsSafe String,
  val status: CodeReviewCIJobState,
  val isRequired: Boolean,
  val detailsUrl: @NlsSafe String?
)