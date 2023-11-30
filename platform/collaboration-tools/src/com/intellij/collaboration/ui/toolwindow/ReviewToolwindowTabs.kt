// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

/**
 * Represents the state of review toolwindow tabs
 *
 * @param T tab type
 * @param TVM tab view model
 */
data class ReviewToolwindowTabs<T : ReviewTab, TVM : ReviewTabViewModel>(
  val tabs: Map<T, TVM>,
  val selectedTab: T?
)
