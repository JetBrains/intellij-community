// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import javax.swing.Icon

class SkipImportAction : DumbAwareAction(), LinkAction {
  init {
    templatePresentation.text = "Skip Import"
    templatePresentation.icon = null
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

}

interface LinkAction