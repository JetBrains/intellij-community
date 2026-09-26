// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.annotations.ApiStatus

/**
 * One line of a [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file that excludes a file.
 */
@ApiStatus.Internal
class AnalysisIgnoreExclusion(
  val baseDir: VirtualFile,
  val pattern: AnalysisIgnorePattern,
  val excludedFile: VirtualFile,
) {
  /** The `.analysisignore` file that holds [pattern], or `null`. */
  val ignoreFile: VirtualFile?
    get() = baseDir.findChild(ANALYSIS_IGNORE_FILE_NAME)?.takeUnless { it.isDirectory }
}

/**
 * Returns every line of a `.analysisignore` file that excludes [file]. A line that matches [file] itself comes first.
 */
@ApiStatus.Internal
fun findAnalysisIgnoreExclusions(project: Project, file: VirtualFile): List<AnalysisIgnoreExclusion> {
  if (!runReadAction { ProjectFileIndex.getInstance(project).isExcluded(file) }) return emptyList()

  val entities = WorkspaceModel.getInstance(project).currentSnapshot.entities<AnalysisIgnoreEntity>().toList()
  if (entities.isEmpty()) return emptyList()

  val result = ArrayList<AnalysisIgnoreExclusion>()
  for (entity in entities) {
    val baseDir = entity.baseDir.virtualFile ?: continue
    if (!VfsUtilCore.isAncestor(baseDir, file, true)) continue

    val patterns = AnalysisIgnorePattern.compileAll(entity.patterns, baseDir.isCaseSensitive)
    var current = file
    while (current != baseDir) {
      val relativePath = VfsUtilCore.getRelativePath(current, baseDir, '/') ?: break
      for (pattern in patterns) {
        if (pattern.matches(relativePath, current.name, current.isDirectory)) {
          result.add(AnalysisIgnoreExclusion(baseDir, pattern, current))
        }
      }
      current = current.parent ?: break
    }
  }
  // The files of one branch: a longer path lies deeper. The sort is stable, and thus the lines of one file keep their order.
  result.sortByDescending { it.excludedFile.path.length }
  return result
}
