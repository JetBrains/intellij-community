// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace

import com.intellij.ide.scratch.RootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.workspaceModel.ide.impl.WorkspaceEntityLifecycleSupporterUtils

class ScratchWorkspaceStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val listener = {
      val provider = WorkspaceEntityLifecycleSupporter.EP_NAME.findExtensionOrFail(ScratchEntityLifecycleSupporter::class.java)
      WorkspaceEntityLifecycleSupporterUtils.ensureEntitiesInWorkspaceAreAsProviderDefined(project, provider)
    }
    RootType.ROOT_EP.addChangeListener(listener, project.service<ScratchDisposableService>())
  }
}

@Service(Service.Level.PROJECT)
class ScratchDisposableService : Disposable {
  override fun dispose() {
  }
}

