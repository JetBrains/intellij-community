// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.LanguageCallHierarchy
import com.intellij.ide.hierarchy.LanguageMethodHierarchy
import com.intellij.ide.hierarchy.LanguageTypeHierarchy
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText

class ActivateHierarchyToolWindowAction : ToolWindowEmptyStateAction(ToolWindowId.HIERARCHY, AllIcons.Toolwindows.ToolWindowHierarchy) {
  override fun setupEmptyText(project: Project, text: StatusText) {
    text.clear()
    text.appendLine(LangBundle.message("hierarchy.empty.text"))
    if (LanguageTypeHierarchy.INSTANCE.hasAnyExtensions()) {
      text.appendLine(LangBundle.message("hierarchy.empty.text.type", KeymapUtil.getShortcutText(IdeActions.ACTION_TYPE_HIERARCHY)))
    }
    if (LanguageCallHierarchy.INSTANCE.hasAnyExtensions()) {
      text.appendLine(LangBundle.message("hierarchy.empty.text.call", KeymapUtil.getShortcutText(IdeActions.ACTION_CALL_HIERARCHY)))
    }
    if (LanguageMethodHierarchy.INSTANCE.hasAnyExtensions()) {
      text.appendLine(LangBundle.message("hierarchy.empty.text.method", KeymapUtil.getShortcutText(IdeActions.ACTION_METHOD_HIERARCHY)))
    }
    text.appendLine("")
    text.appendLine(AllIcons.General.ContextHelp, LangBundle.message("hierarchy.empty.text.help"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
      HelpManager.getInstance().invokeHelp("procedures.viewinghierarchy")
    }
  }

  override fun ensureToolWindowCreated(project: Project) {
    HierarchyBrowserManager.getInstance(project)
  }
}