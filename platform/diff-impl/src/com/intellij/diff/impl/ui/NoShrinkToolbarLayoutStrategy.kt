// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

@ApiStatus.Internal
class NoShrinkToolbarLayoutStrategy : ToolbarLayoutStrategy by ToolbarLayoutStrategy.NOWRAP_STRATEGY {
  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension? {
    return calcPreferredSize(toolbar)
  }
}
