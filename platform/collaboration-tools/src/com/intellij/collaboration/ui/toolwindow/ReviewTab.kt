// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Represents a single closable tab in review toolwindow (e.g. review details tab).
 */
interface ReviewTab {
  /**
   * Unique id used to distinguish tabs that can be reused,
   * so if tab with the same [id] is requested to open by [ReviewTabsController]
   * it will be reused if [reuseTabOnRequest] is [true]
   * or closed and new one will be opened.
   *
   * Also, this id is used for close requests passed to [ReviewTabsController]
   */
  val id: @NonNls String

  /**
   * Toolwindow tab title
   */
  val displayName: @Nls String

  /**
   * If [true] open requests to [ReviewTabsController] will select opened tabs if tabs with the same [id] exists,
   * otherwise existed tab with the [id] will be closed and new tab opened
   */
  val reuseTabOnRequest: Boolean
}