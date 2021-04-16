// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.util.ui.UIUtil
import java.util.*
import javax.swing.JFrame

class WindowActionPromoter: ActionPromoter {
  override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
    val window = UIUtil.getWindow(context.getData(PlatformDataKeys.CONTEXT_COMPONENT))
    if (window != null && window !is JFrame)
      return ArrayList(actions.filter { it is WindowAction })
    else return Collections.emptyList()
  }
}