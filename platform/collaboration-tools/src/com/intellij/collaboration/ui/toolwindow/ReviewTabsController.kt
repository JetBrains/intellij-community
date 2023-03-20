// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import kotlinx.coroutines.flow.Flow

/**
 * Controller that allows to pass open and close tabs requests to a toolwindow tabs manager.
 * Actions can acquire this controller from data context using [ReviewToolwindowDataKeys.REVIEW_TABS_CONTROLLER]
 *
 * Note: closing tab by toolwindow mechanisms (like pressing cross or shortcut) won't trigger [closeReviewTabRequest]
 */
interface ReviewTabsController<T : ReviewTab> {
  val openReviewTabRequest: Flow<T>

  val closeReviewTabRequest: Flow<T>
}