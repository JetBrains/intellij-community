// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.OrderRootsCacheBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import kotlinx.coroutines.CoroutineScope

class ProjectRootManagerBridge(project: Project, coroutineScope: CoroutineScope) : ProjectRootManagerComponent(project, coroutineScope) {
  override fun getOrderRootsCache(project: Project): OrderRootsCache {
    return OrderRootsCacheBridge(project, project)
  }
}