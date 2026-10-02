package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

class ExtractException(override val message: @Nls String, val file: PsiFile, val problems: List<TextRange> = emptyList()): RuntimeException(message) {
  constructor(message: @Nls String, problems: List<PsiElement>): this(message, problems.first().containingFile, problems.map { it.textRange })
  constructor(message: @Nls String, problem: PsiElement): this(message, listOf(problem))
  constructor(message: @Nls String, file: PsiFile): this(message, file, emptyList())
}