// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.usageView.UsageInfo

class JavaAutomaticTestMethodRenamerFactory : AutomaticTestMethodRenamerFactory() {
  override fun isApplicable(element: PsiElement): Boolean {
    if (element !is PsiMethod) return false
    return !TestFrameworks.getInstance().isTestMethod(element)
  }

  override fun isEnabled(): Boolean = JavaRefactoringSettings.getInstance().isRenameTestMethods


  override fun setEnabled(enabled: Boolean) {
    JavaRefactoringSettings.getInstance().isRenameTestMethods = enabled
  }

  override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>): AutomaticRenamer {
    val psiMethod = element as? PsiMethod
    val psiClass = psiMethod?.containingClass
    val module = if (psiClass != null) ModuleUtil.findModuleForPsiElement(psiClass) else null
    return AutomaticTestMethodRenamer(
      psiMethod?.name,
      psiClass?.name,
      module,
      newName)
  }
}