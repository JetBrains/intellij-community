package com.intellij.microservices.url.parameters

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext

internal class PathVariableRenameInputValidator : RenameInputValidator {
  override fun getPattern(): ElementPattern<out PsiElement?> {
    return PlatformPatterns.psiElement(PathVariablePsiElement::class.java)
  }

  override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean {
    val pathVariableElement = element as? PathVariablePsiElement ?: return false
    return pathVariableElement.nameValidationPattern.matcher(newName).matches()
  }
}