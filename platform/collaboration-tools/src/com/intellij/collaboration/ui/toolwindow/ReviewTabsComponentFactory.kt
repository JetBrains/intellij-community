// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

/**
 * Provides UI components for review toolwindow tabs and toolwindow empty state.
 */
interface ReviewTabsComponentFactory<T : ReviewTab, VM : ReviewToolwindowProjectViewModel<T>> {
  /**
   * Provide a review list component for given [projectVm]
   *
   * @param cs scope that closes when context is changed
   */
  fun createReviewListComponent(cs: CoroutineScope, projectVm: VM): JComponent

  /**
   * Provides a component for given [reviewTabType] and [projectVm]
   *
   * @param cs scope that closes when tab is closed or context changed
   */
  fun createTabComponent(cs: CoroutineScope, projectVm: VM, reviewTabType: T): JComponent

  /**
   * Provides a component that should be shown in toolwindow when there are no [ReviewToolwindowProjectViewModel]
   *
   * In most cases, this component should provide a way to log in and select a project
   *
   * @param cs scope that closes when [ReviewToolwindowProjectViewModel] appears (e.g. user is logged in)
   */
  fun createEmptyTabContent(cs: CoroutineScope): JComponent
}