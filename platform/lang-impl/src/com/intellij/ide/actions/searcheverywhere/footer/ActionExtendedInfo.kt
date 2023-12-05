// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.footer

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import java.util.function.Supplier

internal fun createActionExtendedInfo(project: Project?): ExtendedInfo {
  val description = fun(it: Any): String? {
    return when (val value = (it as? MatchedValue)?.value ?: return null) {
      is GotoActionModel.ActionWrapper -> value.action.templatePresentation.description
      is AnAction -> value.templatePresentation.description
      is OptionDescription -> value.option
      else -> null
    }
  }

  val shortcut = fun(it: Any?): AnAction? = (it as? MatchedValue)?.let { AssignShortcutAction(project, it) }
  return ExtendedInfo(description, shortcut)
}

private class AssignShortcutAction(private val project: Project?, private val value: MatchedValue) :
  AnAction(Supplier { LangBundle.message("label.assign.shortcut") },
           Supplier {
             LangBundle.message("actions.tab.assign.a.shortcut", KeymapUtil.getFirstKeyboardShortcutText(
               KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
             )
           }) {
  init {
    shortcutSet = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ActionSearchEverywhereContributor.showAssignShortcutDialog(project, value)
  }
}