// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.ide.projectView.impl.nodes

import com.intellij.openapi.module.ModuleDescription
import com.intellij.openapi.module.impl.LoadedModuleDescriptionImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.annotations.ApiStatus.Internal

public fun moduleDescriptions(project: Project): List<ModuleDescription> {
  val snapshot = WorkspaceModel
    .getInstance(project)
    .currentSnapshot
  return snapshot
    .entities(ModuleEntity::class.java)
    .filter { module: ModuleEntity ->
      ProgressManager.checkCanceled()
      !module.sourceRoots.isEmpty()
    }
    .mapNotNull { module: ModuleEntity ->
      module.findModule(snapshot)
    }
    .map {
      LoadedModuleDescriptionImpl(it)
    }
    .toList()
}
