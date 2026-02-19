// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.create

import kotlinx.coroutines.flow.StateFlow

interface CodeReviewTitleDescriptionViewModel {
  val titleText: StateFlow<String>
  val descriptionText: StateFlow<String>
  val isTemplateLoading: StateFlow<Boolean>

  fun setTitle(text: String)
  fun setDescription(text: String)
}