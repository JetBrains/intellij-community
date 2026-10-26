// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.ProjectRootEntity
import org.jetbrains.annotations.ApiStatus

internal const val ANALYSIS_IGNORE_DEFAULTS_ENABLED_KEY: String = "ide.analysisignore.defaults.enabled"

/**
 * The exclusions of a project root without a [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file in the root directory. A
 * [default entity][AnalysisIgnoreDefaultEntitySource] holds [lines] for such a root, and the file in the root directory removes that
 * entity. A new file in the root directory gets the lines first, so that it states every exclusion of the root, and the user edits each of
 * them there. A file below the root gets no lines: its lines add to the defaults. The user edits [lines] on the
 * [Default Exclusions][AnalysisIgnoreDefaultsConfigurable] page.
 */
@ApiStatus.Internal
object AnalysisIgnoreDefaults {
  /** Matched by the exact name at any depth below the directory of the file. */
  val EXCLUDED_ANYWHERE: List<String> = listOf(
    ".ccache",
    ".git",
    ".hg",
    ".jj",
    ".mypy_cache",
    ".next",
    ".parcel-cache",
    ".pnpm-store",
    ".pytest_cache",
    ".ruff_cache",
    ".svn",
    ".tox",
    ".venv",
    ".yarn",
    "CVS",
    "__pycache__",
    "node_modules",
  )

  /** Matched only directly under a root, where these names hold its build output. */
  val EXCLUDED_AT_ROOT: List<String> = listOf(
    ".cache",
    ".gradle",
    "bin",
    "dist",
    "obj",
    "out",
    "target",
    "venv",
  )

  /** The lines of the product, in the order of a file. [lines] are these lines until the user edits them. */
  val BUILT_IN_LINES: List<String> = EXCLUDED_ANYWHERE + EXCLUDED_AT_ROOT.map { "/$it/" }

  /** The patterns of a [default entity][AnalysisIgnoreDefaultEntitySource], in the order of a file. */
  val lines: List<String>
    get() = AnalysisIgnoreDefaultsSettings.getInstance().lines

  /** Returns `true` while the [feature][ANALYSIS_IGNORE_ENABLED_KEY] and its [defaults][ANALYSIS_IGNORE_DEFAULTS_ENABLED_KEY] are on. */
  fun areEnabled(): Boolean = Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true) && Registry.`is`(ANALYSIS_IGNORE_DEFAULTS_ENABLED_KEY, true)

  /**
   * Returns the default lines of a new file in [baseDir]. A new file in a project root directory gets [lines]. A file in any other
   * directory gets no lines. The result is empty while the defaults are off.
   */
  fun linesOfNewFileIn(project: Project, baseDir: VirtualFile): List<String> {
    if (!areEnabled()) return emptyList()
    val isProjectRoot = WorkspaceModel.getInstance(project).currentSnapshot.entities<ProjectRootEntity>().any { it.root.url == baseDir.url }
    return if (isProjectRoot) lines else emptyList()
  }

  /** Returns `true` if [line] is a pattern that the format supports. A blank line and a comment are not patterns. */
  fun isSupportedLine(line: String): Boolean {
    val source = AnalysisIgnorePattern.patternSource(line) ?: return false
    return source == line && AnalysisIgnorePattern.validate(source) == AnalysisIgnoreValidated.Supported
  }
}

/** Returns the entities of the `.analysisignore` files, without the default entities. */
internal fun EntityStorage.fileEntities(): Sequence<AnalysisIgnoreEntity> =
  entities<AnalysisIgnoreEntity>().filter { it.entitySource == AnalysisIgnoreEntitySource }

/** Returns the [default entities][AnalysisIgnoreDefaultEntitySource] of the project roots. */
internal fun EntityStorage.defaultEntities(): List<AnalysisIgnoreEntity> =
  entities<AnalysisIgnoreEntity>().filter { it.entitySource == AnalysisIgnoreDefaultEntitySource }.toList()
