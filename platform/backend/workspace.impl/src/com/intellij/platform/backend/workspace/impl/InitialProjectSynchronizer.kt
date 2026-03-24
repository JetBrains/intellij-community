// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeLoaderService

internal class InitialProjectSynchronizer : InitProjectActivity {
  override suspend fun run(project: Project) {
    project.serviceAsync<ModuleBridgeLoaderService>().loadForProject(project)
  }
}