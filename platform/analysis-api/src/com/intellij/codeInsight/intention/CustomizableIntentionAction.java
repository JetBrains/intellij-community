// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.openapi.util.NlsContexts;

/**
 * Intention action UI representation of which can be customized
 *
 * Mostly, it can be used to:
 * * forcefully disable submenu,
 * * make intention action non-selectable
 * * fully remove icon
 * * create custom tooltip text
 */
public interface CustomizableIntentionAction extends IntentionAction {
  /**
   * Define if submenu (or so-called options)
   * of intention action should be shown
   */
  boolean isShowSubmenu();

  /**
   * Define if element in popup should be
   * selectable
   */
  boolean isSelectable();

  /**
   * Define if icon should be shown or
   * completely removed
   */
  boolean isShowIcon();

  /**
   * Get text specifically for tooltip view
   */
  @NlsContexts.Tooltip
  default String getTooltipText() {
    return getText();
  }
}
