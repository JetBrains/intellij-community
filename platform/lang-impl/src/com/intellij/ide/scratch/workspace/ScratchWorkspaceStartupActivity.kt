// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.ide.scratch.RootType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.workspaceModel.ide.impl.WorkspaceEntityLifecycleSupporterUtils
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private class ScratchWorkspaceStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    coroutineScope {
      RootType.ROOT_EP.addChangeListener(coroutineScope = this) {
        val provider = WorkspaceEntityLifecycleSupporter.EP_NAME.findExtensionOrFail(ScratchEntityLifecycleSupporter::class.java)
        launch {
          WorkspaceEntityLifecycleSupporterUtils.ensureEntitiesInWorkspaceAreAsProviderDefined(project, provider)
        }
      }
      awaitCancellation()
    }
  }
}