// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Tells if the "Project" pane of the Project view can select a file.
 *
 * The "Select In" action and the project root node use this check.
 * The "Packages" pane and the scope panes do not use it.
 * The pane can select a file when at least one contributor supports it.
 * A file that no contributor supports does not show as a selection target.
 *
 * Register an implementation in `plugin.xml`:
 * ```xml
 * <projectPaneSelectableFileContributor implementation="com.example.MyProjectPaneSelectableFileContributor"/>
 * ```
 */
@ApiStatus.OverrideOnly
fun interface ProjectPaneSelectableFileContributor {
  /**
   * Returns `true` if the "Project" pane can select [virtualFile] in [project].
   *
   * Return `false` for a file that this contributor does not know.
   * Another contributor can then support the file.
   */
  fun isSelectable(project: Project, virtualFile: VirtualFile): Boolean
}
