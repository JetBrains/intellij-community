// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

internal class AnalysisIgnoreWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<AnalysisIgnoreEntity> {
  override val entityClass: Class<AnalysisIgnoreEntity>
    get() = AnalysisIgnoreEntity::class.java

  override fun registerFileSets(entity: AnalysisIgnoreEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) return

    val baseDir = entity.baseDir
    val baseDirFile = baseDir.virtualFile
    val caseSensitive = baseDirFile?.isCaseSensitive ?: SystemInfoRt.isFileSystemCaseSensitive

    val patterns = AnalysisIgnorePattern.compileAll(entity.patterns, caseSensitive)
    if (patterns.isEmpty()) return

    registrar.registerUnscopedExclusionCondition(baseDir, AnalysisIgnoreMatcher(baseDir.url, baseDirFile, patterns, caseSensitive), entity)
  }
}
