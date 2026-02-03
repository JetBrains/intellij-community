// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
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

    private val LOG = logger<VirtualFileCustomDataProvider<*>>()

    fun getPsiFileSafe(virtualFile: VirtualFile, project: Project): PsiFile? {
      // RDCT-1091
      // PsiManager.getInstance(project).findFile(virtualFile) creates FileViewProvider for LightVirtualFile
      // in any Project. But FileViewProvider can exist for LightVirtualFile only in 1 project
      // (see com.intellij.psi.impl.file.impl.FileManagerImpl.checkLightFileHasNoOtherPsi).
      if (virtualFile is LightVirtualFile &&
          PsiManager.getInstance(project).findCachedViewProvider(virtualFile) == null) {
        LOG.trace { "ignore LightVirtualFile: file=${virtualFile} project=${project} dataProvider=${this::class.java}" }
        return null
      }

      return PsiManager.getInstance(project).findFile(virtualFile)
    }
  }

  /**
   * Called from the backend side
   */
  fun getValues(project: Project, virtualFile: VirtualFile): Flow<TData>
}