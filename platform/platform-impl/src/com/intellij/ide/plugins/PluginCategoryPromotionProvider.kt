
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

// @spec platform/platform-impl/spec/plugin-manager/unified-plugin-manager-ui.spec.md
/** Provides a promotion panel and display priority for one exact Bundled category name. */
@ApiStatus.Internal
interface PluginCategoryPromotionProvider {
  /** Returns the Bundled category name. */
  fun getCategoryName(): String

  /** Returns the panel that the expanded Bundled category shows below its header. */
  fun createPromotionPanel(): JComponent?

  /** Returns whether the Bundled section puts this category before other healthy categories. */
  fun isPriorityCategory(): Boolean = false
}
