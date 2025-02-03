// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class OpenSettingsJbProtocolService : JBProtocolCommand("settings") {
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): @DialogMessage String? =
    parameter(parameters, "name").let { name ->
      if (Util.doOpenSettings(name)) null
      else IdeBundle.message("jb.protocol.settings.no.configurable", name)
    }

  object Util {
    @Suppress("ForbiddenInSuspectContextMethod")
    fun doOpenSettings(name: String): Boolean {
      val project = RestService.getLastFocusedOrOpenedProject() ?: ProjectManager.getInstance().defaultProject
      val configurable = SearchConfigurableByNameHelper(name, project).searchByName() ?: return false
      ApplicationManager.getApplication().invokeLater(
        Runnable {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable)
                 },
        project.disposed)
      return true
    }
  }
}
