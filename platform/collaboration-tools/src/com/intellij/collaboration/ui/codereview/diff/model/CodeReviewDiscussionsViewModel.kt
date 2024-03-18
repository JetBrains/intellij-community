// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.model

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import kotlinx.coroutines.flow.StateFlow

/**
 * A view model somehow responsible for review discussions
 */
interface CodeReviewDiscussionsViewModel {

  val discussionsViewOption: StateFlow<DiscussionsViewOption>

  fun setDiscussionsViewOption(viewOption: DiscussionsViewOption)
}