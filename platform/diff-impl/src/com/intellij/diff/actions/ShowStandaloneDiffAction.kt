// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions

import com.intellij.diff.editor.DiffEditorTabFilesUtil
import com.intellij.diff.tools.external.ExternalDiffTool
import com.intellij.idea.ActionsBundle.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.actionSystem.ExtendableAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAware

/**
 * In contrast to [ShowDiffAction] which may show diff preview, this action will show diff without selection tracking.
 */
internal class ShowStandaloneDiffAction : ExtendableAction(EP_NAME), DumbAware {
  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName.create<AnActionExtensionProvider>(
      "com.intellij.diff.actions.ShowStandaloneDiffAction.ExtensionProvider")
  }

  init {
    isEnabledInModalContext = true
  }

  override fun commonUpdate(e: AnActionEvent) {
    val project = e.project

    with(e.presentation) {
      if (DiffEditorTabFilesUtil.isDiffInEditor) {
        text = message("action.Diff.ShowStandaloneDiff.tab.text")
        description = message("action.Diff.ShowStandaloneDiff.tab.description")
      }
      else {
        text = message("action.Diff.ShowStandaloneDiff.window.text")
        description = message("action.Diff.ShowStandaloneDiff.window.description")
      }

      isEnabledAndVisible = project != null &&
                            !ExternalDiffTool.isDefault()
    }
  }
}
