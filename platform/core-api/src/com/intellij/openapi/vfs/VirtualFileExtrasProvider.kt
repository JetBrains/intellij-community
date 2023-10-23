// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow

interface VirtualFileExtrasProvider<TData : Any> : VirtualFileExtras<TData> {
  companion object {
    val EP_NAME: ExtensionPointName<VirtualFileExtrasProvider<*>> = ExtensionPointName.create("com.intellij.virtualFile.extras.provider")
  }

  /**
   * Called from the backend side
   */
  fun getValues(project: Project, virtualFile: VirtualFile): Flow<TData>

}