// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.importer

import com.intellij.importSettings.data.JBrActionsDataProvider
import com.intellij.importSettings.data.TestJbService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SettingSyncAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = SettingSyncDialog(JBrActionsDataProvider.getInstance(), TestJbService.main)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()
  }
}