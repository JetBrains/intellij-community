// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.SmartList

private class RunToolbarActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): MutableList<AnAction> {
    for (action in actions) {
      if (action is RunToolbarProcessAction) {
        return SmartList(action)
      }
    }
    return emptyList<AnAction>().toMutableList()
  }
}