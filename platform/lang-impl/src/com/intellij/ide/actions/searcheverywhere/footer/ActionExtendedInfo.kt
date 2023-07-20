// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.footer

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project

fun createActionExtendedInfo(project: Project?): ExtendedInfo {
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

class AssignShortcutAction(val project: Project?, val value: MatchedValue) : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ActionSearchEverywhereContributor.showAssignShortcutDialog(project, value)
  }

  override fun update(e: AnActionEvent) {
    val action = ActionSearchEverywhereContributor.getAction(value)
    if (action != null) {
      e.presentation.text =
        if (KeymapUtil.getActiveKeymapShortcuts(ActionManager.getInstance().getId(action)).shortcuts.isEmpty()) {
          LangBundle.message("label.assign.shortcut")
        }
        else {
          LangBundle.message("label.change.shortcut")
        }
      e.presentation.description = LangBundle.message("actions.tab.assign.a.shortcut", altEnter())
    }
  }

  private fun altEnter() = KeymapUtil.getFirstKeyboardShortcutText(
    KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
}