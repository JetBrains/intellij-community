// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this interface and register the implementation as `com.intellij.workspaceModel.optionalExclusionContributor` extension in plugin.xml.
 * This extension point allows customizing platform actions excluding / unexcluding files or directories from the Workspace Model.
 * For example, the following actions respect it:
 *  * "Mark Directory As | Excluded": Available from the context menu on non-excluded directories in the Project View.
 *  * "Mark Directory As | Cancel Exclusion": Available from the context menu on excluded directories in the Project View.
 *
 * The purpose of this extension point is to ensure proper behavior of these actions for files / directories
 * excluded from the Workspace Model by [com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy] or
 * [com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor].
 *
 * @see OptionalExclusionUtil
 */
@ApiStatus.Experimental
interface OptionalExclusionContributor {
  /**
   * Invoked when a file/directory is requested to be excluded.
   * It can be invoked on any thread with or without read action.
   * If the implementation can exclude the [fileOrDir], it should return `true` and exclude it asynchronously.
   *
   * @return true if the [fileOrDir] is expected to be excluded eventually
   */
  fun requestExclusion(project: Project, fileOrDir: VirtualFile): Boolean

  /**
   * Checks if an excluded file/directory can be unexcluded.
   * It can be invoked on any thread with or without read action.
   */
  fun canCancelExclusion(project: Project, excludedFileOrDir: VirtualFile): Boolean

  /**
   * Invoked when an excluded file/directory is requested to be unexcluded.
   * It can be invoked on any thread with or without read action.
   * If the implementation can handle the request, it should return `true` and cancel the exclusion asynchronously.
   *
   * @return true if the exclusion of [excludedFileOrDir] is expected to be canceled eventually
   */
  fun requestExclusionCancellation(project: Project, excludedFileOrDir: VirtualFile): Boolean
}
