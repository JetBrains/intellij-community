// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Defines where the caret is placed once the navigation reached its target.
 *
 * @see NavigationOptions.caretPlacement
 */
@Internal
enum class CaretPlacement {
  /**
   * Places the caret at the offset of the navigation request.
   */
  TARGET_OFFSET,

  /**
   * Places the caret behind the token which starts at the offset of the navigation request,
   * so that the navigated name is left behind the caret, as if it were typed.
   */
  TOKEN_END,
}
