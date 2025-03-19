// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.ApiStatus;

/**
 * Indicates a place where intention actions UI is shown, used primarily for usage data.
 * Currently, this information cannot be used by an intention's implementation; it's used by the platform only.
 */
@ApiStatus.Experimental
public enum IntentionSource {
  /**
   * A tooltip on a highlighted element.
   */
  DAEMON_TOOLTIP,

  /**
   * `Show Context Actions` context menu action and shortcut calls.
   */
  CONTEXT_ACTIONS,

  /**
   * Floating light bulb shown for a caret position in editors.
   */
  LIGHT_BULB,

  /**
   * Actions popup expanded in the Floating Toolbar.
   */
  FLOATING_TOOLBAR,

  /**
   * The file level notification panel in editors.
   */
  FILE_LEVEL_ACTIONS,

  /**
   * Quick fixes button in the Problems tool window.
   */
  PROBLEMS_VIEW,

  /**
   * The Search Everywhere popup.
   */
  SEARCH_EVERYWHERE,

  /**
   * A shortcut assigned to an intention action.
   */
  CUSTOM_SHORTCUT,

  /**
   * Non-standard place.
   */
  OTHER
}
