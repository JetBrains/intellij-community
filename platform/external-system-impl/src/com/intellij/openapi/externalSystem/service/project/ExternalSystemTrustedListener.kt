// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.ide.impl.TrustStateListener
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project

internal class ExternalSystemTrustedListener : TrustStateListener {
  override fun onProjectTrustedFromNotification(project: Project) {
    ExternalSystemManager.EP_NAME.forEachExtensionSafe {
      val settings = it.settingsProvider.`fun`(project)
      if (settings.linkedProjectsSettings.isNotEmpty()) {
        for (linkedProjectSettings in settings.linkedProjectsSettings) {
          val externalProjectPath = linkedProjectSettings.externalProjectPath
          ExternalSystemUtil.refreshProject(externalProjectPath, ImportSpecBuilder(project, it.systemId))
        }
      }
    }
  }
}