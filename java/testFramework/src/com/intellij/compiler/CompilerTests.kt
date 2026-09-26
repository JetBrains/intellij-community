// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CompilerTests")
package com.intellij.compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache.Companion.getInstance
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

fun saveWorkspaceModelCaches(project: Project) {
  if (Registry.`is`("jps.build.use.workspace.model")) {
    val cache = WorkspaceModelCache.getInstance(project)
    checkNotNull(cache) { "Workspace model cache is not enabled for project" }
    cache.setVirtualFileUrlManager(WorkspaceModel.getInstance(project).getVirtualFileUrlManager())
    cache.saveCacheNow()
    val globalCache = getInstance()
    checkNotNull(globalCache) { "Workspace model cache is not enabled for global storage" }
    runWithModalProgressBlocking(project, "Save workspace model cache") {
      globalCache.saveCacheNow()
    }
  }
}
