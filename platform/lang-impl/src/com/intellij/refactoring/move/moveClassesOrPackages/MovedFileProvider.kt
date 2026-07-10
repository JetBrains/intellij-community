// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages

import com.intellij.openapi.components.service
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MovedFileProvider {
  /**
   * @param destination the directory the file was moved into
   * @param file the file as it was before the move; its PSI may be invalid afterwards
   * @returns the [PsiFile] that further processing of the move refactoring should operate on,
   * after [file] has already been moved into [destination].
   */
  fun getUpdatedFile(destination: PsiDirectory, file: PsiFile): PsiFile?

  companion object {
    @JvmStatic
    fun getInstance(): MovedFileProvider = service()
  }
}