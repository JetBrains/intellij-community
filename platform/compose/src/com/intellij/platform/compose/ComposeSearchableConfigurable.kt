// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.runtime.Composable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.JewelComposePanel
import javax.swing.JComponent

/**
 * A [SearchableConfigurable] that uses Compose UI for rendering.
 *
 * Currently only available in internal mode.
 */
@ApiStatus.Internal
abstract class ComposeSearchableConfigurable : SearchableConfigurable, Configurable.NoScroll {
  /**
   * Compose function that contains content of this configurable.
   */
  @Composable
  open fun ComposeContent() {
  }

  override fun createComponent(): JComponent? =
    if (ApplicationManager.getApplication().isInternal)
      JewelComposePanel { ComposeContent() }
    else null

}