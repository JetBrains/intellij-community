// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MoveFileInvalidationStrategy {
  /**
   * Controls whether the file should be invalidated after performing the move refactoring,
   *
   * @param directory the directory to which the file is moved
   * @param file the file to be invalidated
   */
  fun invalidateFile(directory: PsiDirectory, file: PsiFile): PsiFile?

  companion object {
    private val EP_NAME: ExtensionPointName<MoveFileInvalidationStrategy> = ExtensionPointName.create("com.intellij.refactoring.moveFileInvalidationStrategy")

    @JvmStatic
    fun invalidate(directory: PsiDirectory, file: PsiFile): PsiFile? {
      return EP_NAME.extensionList.firstOrNull()?.invalidateFile(directory, file)
    }
  }
}
