// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ConfigAction : DumbAwareAction() {
  companion object {
    val name = "Config or Installation Directory"
    val icon = AllIcons.Ide.ConfigFile
  }

  init {
    templatePresentation.text = name
    templatePresentation.icon = icon
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {

  }
}