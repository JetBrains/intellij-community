// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls

/**
 * View model of the review toolwindow tab content
 */
//TODO: move name and description to component factory and potentially remove this class
interface ReviewTabViewModel {
  /**
   * Toolwindow tab title
   */
  val displayName: @Nls String

  /**
   * Toolwindow tab tooltip
   */
  val description: @NlsContexts.Tooltip String
    get() = displayName
}