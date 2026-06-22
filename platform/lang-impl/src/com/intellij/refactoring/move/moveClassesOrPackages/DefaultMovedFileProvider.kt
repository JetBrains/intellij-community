// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

internal class DefaultMovedFileProvider : MovedFileProvider {
  override fun getUpdatedFile(destination: PsiDirectory, file: PsiFile): PsiFile? = destination.findFile(file.name)
}