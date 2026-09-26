// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use SetEditorSettingsActionGroup instead",
            replaceWith = ReplaceWith("SetEditorSettingsActionGroup", "com.intellij.diff.actions.impl.SetEditorSettingsActionGroup"))
class SetEditorSettingsAction @ApiStatus.Internal constructor() :
  DumbAwareAction(DiffBundle.message("editor.settings"), null, AllIcons.General.GearPlain) {

  override fun actionPerformed(e: AnActionEvent) {
    // deprecated
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
