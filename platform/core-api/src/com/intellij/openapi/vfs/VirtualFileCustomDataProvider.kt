// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Used for synchronizing custom data related to a [VirtualFile] instance
 * from Host to Thin Client in Remote Development. Provides the custom data,
 * that should be sent from Host.
 */
@ApiStatus.Experimental
interface VirtualFileCustomDataProvider<TData : Any> : VirtualFileCustomDataSynchronizer<TData> {
  companion object {
    val EP_NAME: ExtensionPointName<VirtualFileCustomDataProvider<*>> =
      ExtensionPointName.create("com.intellij.virtualFileCustomDataProvider")
  }

  /**
   * Called from the backend side
   */
  fun getValues(project: Project, virtualFile: VirtualFile): Flow<TData>

}