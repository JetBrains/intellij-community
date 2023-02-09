// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import org.jetbrains.annotations.Nls

/**
 * Represent a context of selected project (for GitHub it is a repository).
 * Usually it is a place where necessary data is hold regarding.
 *
 * Context is mostly used for UI creation in [ReviewTabsComponentFactory]
 * to create the review list component or other tabs.
 */
interface ReviewToolwindowProjectContext {
  /**
   * Presentable name for the project which context is hold here.
   * Used in toolwindow UI places like review list tab title.
   */
  val projectName: @Nls String
}