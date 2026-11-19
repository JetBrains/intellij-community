// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * The application service that gives the workspace of a project. Use [Project.workspace].
 */
@ApiStatus.Internal
interface ProjectWorkspaceManager {
  fun getWorkspace(project: Project): ProjectWorkspace
}
