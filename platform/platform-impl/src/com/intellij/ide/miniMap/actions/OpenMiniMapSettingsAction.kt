// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap.actions

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.miniMap.settings.MiniMapConfigurable
import com.intellij.ide.miniMap.utils.MiniMessagesBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.newEditor.SettingsDialogFactory

class OpenMiniMapSettingsAction : AnAction(MiniMessagesBundle.message("action.settings")) {
  override fun isDumbAware() = true
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true).filter { it.configurables.isNotEmpty() }
    val configurable = ConfigurableVisitor.findById(MiniMapConfigurable.ID, groups)
    val dialog = SettingsDialogFactory.getInstance().create(project, groups, configurable, null)
    dialog.show()
  }
}