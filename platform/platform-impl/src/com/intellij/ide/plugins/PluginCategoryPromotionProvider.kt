
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Extension point for providing promotion panels for specific plugin categories.
 * The promotion panel is displayed under the category title.
 * This provider can also define priority for the category to be displayed first.
 */
@ApiStatus.Internal
interface PluginCategoryPromotionProvider {
  /**
   * Returns the name of the category for which this provider creates a promotion panel.
   */
  fun getCategoryName(): String
  
  /**
   * Creates a promotion panel for the category.
   * @return JComponent to be displayed under the category title, or null if no promotion is needed
   */
  fun createPromotionPanel(): JComponent?
  
  /**
   * Returns whether this category should have priority in the plugin list.
   * Priority categories are displayed first in the installed plugins list.
   * @return true if this category should be prioritized, false otherwise
   */
  fun isPriorityCategory(): Boolean = false
}
