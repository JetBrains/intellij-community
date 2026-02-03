// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.canBeSelected

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CanBeSelectedInProjectPaneProvider {
  fun isSupported(project: Project, virtualFile: VirtualFile): Boolean = true

  companion object {
    private val EP = ExtensionPointName<CanBeSelectedInProjectPaneProvider>("com.intellij.canBeSelectedInProjectPaneProvider")

    @JvmStatic
    fun canBeSelected(project: Project, virtualFile: VirtualFile): Boolean = EP.extensionList.any { it.isSupported(project, virtualFile) }
  }
}