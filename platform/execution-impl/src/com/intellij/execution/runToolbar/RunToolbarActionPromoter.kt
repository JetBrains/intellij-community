// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.util.SmartList

class RunToolbarActionPromoter : ActionPromoter {
  override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
    for (action in actions) {
      if (action is RunToolbarProcessAction) {
        return SmartList(action)
      }
    }
    return emptyList<AnAction>().toMutableList()
  }
}