// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.collaboration.ui.codereview.list.ReviewListViewModel
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Represent a view model of a review toolwindow with selected project (for GitHub it is a repository).
 *
 * VM is mostly used for UI creation in [ReviewTabsComponentFactory]
 * to create the review list component or other tabs.
 *
 * @param T tab type
 * @param TVM tab view model
 */
interface ReviewToolwindowProjectViewModel<T : ReviewTab, TVM : ReviewTabViewModel> {
  /**
   * Presentable name for the project which context is hold here.
   * Used in toolwindow UI places like review list tab title.
   */
  val projectName: @Nls String

  /**
   * ViewModel of the review list view.
   */
  val listVm: ReviewListViewModel

  /**
   * Refresh the toolwindow of the currently opened tab
   */
  @ApiStatus.Internal
  fun refresh() {
    listVm.refresh()
  }

  /**
   * State of displayed review tabs besides the list
   */
  val tabs: StateFlow<ReviewToolwindowTabs<T, TVM>>

  /**
   * Pass a [tab] to select certain review tab or null to select list tab
   */
  fun selectTab(tab: T?)

  fun closeTab(tab: T)
}