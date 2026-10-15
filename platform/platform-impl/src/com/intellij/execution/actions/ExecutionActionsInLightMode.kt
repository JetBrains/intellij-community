// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.platform.ide.productMode.IdeProductMode
import org.jetbrains.annotations.ApiStatus

/**
 * Returns `true` if the current process is running in light mode and execution actions should be hidden.
 * This function is needed only until IJPL-257791 is implemented: after that, the whole execution module won't be loaded in the light mode.
 */
@ApiStatus.Internal
fun areExecutionActionsHiddenInLightMode(): Boolean {
  return IdeProductMode.isLight
}