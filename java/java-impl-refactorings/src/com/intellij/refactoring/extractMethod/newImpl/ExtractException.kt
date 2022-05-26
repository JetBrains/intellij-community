package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.lang.RuntimeException

class ExtractException(message: String, file: PsiFile, val problems: List<TextRange> = emptyList()): RuntimeException(message) {
  constructor(message: String, problems: List<PsiElement>): this(message, problems.first().containingFile, problems.map { it.textRange })
  constructor(message: String, problem: PsiElement): this(message, listOf(problem))
  constructor(message: String, file: PsiFile): this(message, file, emptyList())
}