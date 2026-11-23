// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

/**
 * Provides UI components for review toolwindow tabs and toolwindow empty state.
 */
interface ReviewTabsComponentFactory<TVM : ReviewTabViewModel, PVM : ReviewToolwindowProjectViewModel<*, TVM>> {
  /**
   * Provides the main / home tab for given [projectVm] of the toolwindow
   *
   * @param cs scope that closes when context is changed
   */
  // TODO: to be renamed to createHomeTabComponent or refactored to get rid of, TBD
  fun createReviewListComponent(cs: CoroutineScope, projectVm: PVM): JComponent

  /**
   * Provides a component for given [tabVm] and [projectVm]
   *
   * @param cs scope that closes when tab is closed or context changed
   */
  fun createTabComponent(cs: CoroutineScope, projectVm: PVM, tabVm: TVM): JComponent

  /**
   * Provides a component that should be shown in toolwindow when there are no [ReviewToolwindowProjectViewModel]
   *
   * In most cases, this component should provide a way to log in and select a project
   *
   * @param cs scope that closes when [ReviewToolwindowProjectViewModel] appears (e.g. user is logged in)
   */
  fun createEmptyTabContent(cs: CoroutineScope): JComponent
}