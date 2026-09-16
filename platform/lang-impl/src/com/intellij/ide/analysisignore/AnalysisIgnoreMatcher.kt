// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import org.jetbrains.annotations.ApiStatus

/**
 * The patterns of one [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file, as a condition of the index.
 */
@ApiStatus.Internal
class AnalysisIgnoreMatcher(
  private val baseDirUrl: String,
  private val baseDir: VirtualFile?,
  private val patterns: List<AnalysisIgnorePattern>,
  private val caseSensitive: Boolean,
) : WorkspaceFileSetExclusionCondition {

  // The lines that these patterns come from. The index compares two conditions by these lines.
  private val sources: List<String> = patterns.map { it.source }

  // An anchored pattern needs the path of a file below the directory of the file. Every other pattern needs the name only.
  private val needsRelativePath: Boolean = patterns.any { it.anchored }

  override fun shouldExclude(file: VirtualFile): Boolean {
    if (file == baseDir) return false

    val name = file.nameSequence
    val relativePath = when {
      !needsRelativePath -> name
      baseDir == null -> return false
      else -> VfsUtilCore.getRelativePath(file, baseDir, '/') ?: return false
    }

    return isExcluded(relativePath, name, file.isDirectory)
  }

  /**
   * Returns `true` if the patterns exclude the path.
   */
  fun isExcluded(relativePath: CharSequence, name: CharSequence, isDirectory: Boolean): Boolean {
    // The directory of the file itself. No pattern of the file names it.
    if (relativePath.isEmpty()) return false

    for (i in patterns.indices) {
      if (patterns[i].matches(relativePath, name, isDirectory)) return true
    }
    return false
  }

  override fun equals(other: Any?): Boolean {
    return other is AnalysisIgnoreMatcher &&
           baseDirUrl == other.baseDirUrl &&
           baseDir == other.baseDir &&
           caseSensitive == other.caseSensitive &&
           sources == other.sources
  }

  override fun hashCode(): Int {
    var result = baseDirUrl.hashCode()
    result = 31 * result + baseDir.hashCode()
    result = 31 * result + caseSensitive.hashCode()
    result = 31 * result + sources.hashCode()
    return result
  }
}
