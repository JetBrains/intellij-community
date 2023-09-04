// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.tree

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

class TreeSpeedSearchActionPromoter : ActionPromoter {
  override fun promote(actions: List<out AnAction>, context: DataContext): List<AnAction> {
    return actions.sortedBy { it is TreeSpeedSearchAction }
  }
}
