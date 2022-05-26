// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

private class ReprocessContentRootDataActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    if (ExternalSystemUtil.isNewProject(project)) {
      thisLogger().info("Ignored reprocess of content root data service for new projects")
      return
    }

    val instance = SourceFolderManager.getInstance(project) as SourceFolderManagerImpl
    instance.rescanAndUpdateSourceFolders()
  }
}
