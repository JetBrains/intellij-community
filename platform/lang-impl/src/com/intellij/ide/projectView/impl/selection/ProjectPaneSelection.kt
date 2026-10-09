// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.projectView.impl.selection

import com.intellij.ide.projectView.ProjectPaneSelectableFileContributor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

private val EP = ExtensionPointName<ProjectPaneSelectableFileContributor>("com.intellij.projectPaneSelectableFileContributor")

internal fun isSelectableInProjectPane(project: Project, virtualFile: VirtualFile): Boolean = EP.findFirstSafe { contributor ->
  contributor.isSelectable(project, virtualFile)
} != null