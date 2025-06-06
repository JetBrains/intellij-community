// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MultipleFileOpener {
  suspend fun openFiles(files: List<VirtualFile>, project: Project?): Boolean

  companion object {
    private val epName = ExtensionPointName.create<MultipleFileOpener>("com.intellij.multipleFileOpener")

    suspend fun openFiles(files: List<VirtualFile>, project: Project?): Boolean {
      return epName.extensionList.any { it.openFiles(files, project) }
    }
  }
}