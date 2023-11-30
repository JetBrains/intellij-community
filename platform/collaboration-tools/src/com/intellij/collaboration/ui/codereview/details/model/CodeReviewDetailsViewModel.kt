// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.flow.Flow

interface CodeReviewDetailsViewModel {
  val number: String
  val url: String

  val title: Flow<@NlsSafe String>
  val description: Flow<@NlsSafe String>?
  val reviewRequestState: Flow<ReviewRequestState>
}