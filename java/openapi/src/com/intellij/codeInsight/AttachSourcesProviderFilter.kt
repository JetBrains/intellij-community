// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AttachSourcesProviderFilter {

  /**
   * Checks can be an instance of AttachSourcesProvider applied to the specific PsiFile.
   */
  fun isApplicable(provider: AttachSourcesProvider, libraries: Collection<LibraryEntity>, psiFile: PsiFile): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<AttachSourcesProviderFilter> = ExtensionPointName("com.intellij.attachSourcesProviderFilter")

    @JvmStatic
    fun isProviderApplicable(provider: AttachSourcesProvider, orderEntries: Collection<LibraryEntity>, psiFile: PsiFile): Boolean {
      var applicable = true
      EP_NAME.forEachExtensionSafe {
        if (!it.isApplicable(provider, orderEntries, psiFile)) {
          applicable = false
        }
      }
      return applicable
    }
  }
}