// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.ApiStatus.Internal

private const val SERVICE_NAME = "settings"

@Internal
class OpenSettingsJbProtocolService : JBProtocolCommand(SERVICE_NAME) {

  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? {
    return parameter(parameters, "name").let { name ->
      if (Util.doOpenSettings(name)) null else IdeBundle.message("jb.protocol.settings.no.configurable", name)
    }
  }

  object Util {
    fun doOpenSettings(name: String): Boolean {
      val project = RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
      val configurable = SearchConfigurableByNameHelper(name, project).searchByName() ?: return false
      ApplicationManager.getApplication().invokeLater(
        Runnable { SettingsDialogFactory.getInstance().create(project, SettingsDialog.DIMENSION_KEY, configurable, false, false).show() },
        project.disposed)
      return true
    }
  }
}
