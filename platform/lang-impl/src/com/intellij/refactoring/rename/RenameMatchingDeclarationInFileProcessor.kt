package com.intellij.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Indicates, that rename psi file processor can also rename declaration in this file.
 * Used by Rider for rename file refactoring
 */
interface RenameMatchingDeclarationInFileProcessor {
  fun findMatchingDeclaration(file: PsiFile): PsiElement?
}