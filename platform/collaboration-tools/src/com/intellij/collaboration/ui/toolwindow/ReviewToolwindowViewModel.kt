// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents view model for review toolwindow that holds selected project [projectContext] (for GitHub it is a repository).
 *
 * Clients can provide more specific methods in implementation and acquire the view model using [ReviewToolwindowDataKeys.REVIEW_TOOLWINDOW_VM]
 */
interface ReviewToolwindowViewModel<C : ReviewToolwindowProjectContext> {
  val projectContext: StateFlow<C?>
}