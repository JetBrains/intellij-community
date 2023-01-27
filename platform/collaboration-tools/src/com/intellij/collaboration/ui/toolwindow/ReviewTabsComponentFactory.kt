// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

interface ReviewTabsComponentFactory<T : ReviewTab, C: ReviewToolwindowProjectContext> {
  fun createReviewListComponent(cs: CoroutineScope, projectContext: C): JComponent

  fun createTabComponent(cs: CoroutineScope, projectContext: C, reviewTabType: T): JComponent

  fun createEmptyTabContent(cs: CoroutineScope): JComponent
}