// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import org.jetbrains.annotations.ApiStatus

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

    val registration = splitForRegistration(baseDir, patterns)
    for ((url, pattern) in registration.roots) {
      registrar.registerUnscopedExcludedRoot(url, pattern.directoryOnly, entity)
    }
    if (registration.conditionPatterns.isNotEmpty()) {
      val matcher = AnalysisIgnoreMatcher(baseDir.url, baseDirFile, registration.conditionPatterns, caseSensitive)
      registrar.registerUnscopedExclusionCondition(baseDir, matcher, entity)
    }
  }
}

/**
 * The two ways to register the patterns of one file. [roots] holds the patterns that name one path each, with the URL of that path.
 * [conditionPatterns] holds the patterns that a condition matches at query time.
 */
@ApiStatus.Internal
class AnalysisIgnoreRegistration(
  val roots: List<Pair<VirtualFileUrl, AnalysisIgnorePattern>>,
  val conditionPatterns: List<AnalysisIgnorePattern>,
)

/**
 * Splits [patterns] for the registration. A pattern with a [literalPath][AnalysisIgnorePattern.literalPath] becomes an excluded root by URL.
 */
@ApiStatus.Internal
fun splitForRegistration(baseDir: VirtualFileUrl, patterns: List<AnalysisIgnorePattern>): AnalysisIgnoreRegistration {
  val roots = ArrayList<Pair<VirtualFileUrl, AnalysisIgnorePattern>>()
  val conditionPatterns = ArrayList<AnalysisIgnorePattern>()
  for (pattern in patterns) {
    val literalPath = pattern.literalPath
    if (literalPath == null) {
      conditionPatterns.add(pattern)
    }
    else {
      roots.add(baseDir.append(literalPath) to pattern)
    }
  }
  return AnalysisIgnoreRegistration(roots, conditionPatterns)
}
