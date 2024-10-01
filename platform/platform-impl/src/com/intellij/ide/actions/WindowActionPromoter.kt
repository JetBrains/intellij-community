// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExpandableActions
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.JFrame

@ApiStatus.Internal
class WindowActionPromoter: ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    val window = ComponentUtil.getWindow(context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
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