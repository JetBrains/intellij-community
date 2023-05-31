// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import kotlinx.coroutines.flow.Flow

interface CodeReviewDetailsViewModel {
  val number: String
  val url: String

  val title: Flow<String>
  val description: Flow<String>?
  val reviewRequestState: Flow<ReviewRequestState>
}