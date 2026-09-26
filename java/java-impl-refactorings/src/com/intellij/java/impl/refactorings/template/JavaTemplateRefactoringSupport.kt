// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.refactorings.template

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import org.jetbrains.annotations.ApiStatus

/** Supplies refactoring rules for Java embedded in a template. */
@ApiStatus.Internal
interface JavaTemplateRefactoringSupport {
  fun allowsClassRefactoring(psiClass: PsiClass): Boolean = true
  fun supportsInlineRefactoring(language: Language): Boolean = false
  fun allowsInplaceIntroduceVariable(expression: PsiExpression): Boolean = true

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<JavaTemplateRefactoringSupport> =
      ExtensionPointName.create("com.intellij.java.templateRefactoringSupport")

    @JvmStatic
    fun isClassRefactoringAllowed(psiClass: PsiClass?): Boolean =
      psiClass == null || EP_NAME.extensionList.all { it.allowsClassRefactoring(psiClass) }

    @JvmStatic
    fun isInlineRefactoringSupported(language: Language): Boolean = EP_NAME.extensionList.any { it.supportsInlineRefactoring(language) }

    @JvmStatic
    fun isInplaceIntroduceVariableAllowed(expression: PsiExpression): Boolean =
      EP_NAME.extensionList.all { it.allowsInplaceIntroduceVariable(expression) }
  }
}
