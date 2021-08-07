// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ExpandableActions
import com.intellij.util.ui.UIUtil
import java.util.*
import javax.swing.JFrame

class WindowActionPromoter: ActionPromoter {
  override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
    val window = UIUtil.getWindow(context.getData(PlatformDataKeys.CONTEXT_COMPONENT))
    if (window != null && window !is JFrame
        && !JBPopupFactory.getInstance().isPopupActive
        && actions.any { it is WindowAction }) {
      return ArrayList(actions.sortedWith(Comparator { a1, a2 -> a1.score().compareTo(a2.score()) })
                         .filter { it is WindowAction || it is EditorAction || it is ExpandableActions})
    }
    else return Collections.emptyList()
  }

  private fun AnAction.score():Int {
    if (this is ExpandableActions) return 0
    if (this is EditorAction) return 1
    if (this is WindowAction) return 2
    return 2
  }
}