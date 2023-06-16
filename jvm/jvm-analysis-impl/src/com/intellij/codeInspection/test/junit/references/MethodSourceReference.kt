// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit.references

import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class MethodSourceReference(element: PsiLanguageInjectionHost) : BaseJunitAnnotationReference(element) {
  override fun hasNoStaticProblem(method: PsiMethod, literalClazz: UClass, literalMethod: UMethod?): Boolean {
    val isStatic = method.hasModifierProperty(PsiModifier.STATIC)
    val psiClass = method.containingClass ?: return false
    return method.parameterList.isEmpty && TestUtils.testInstancePerClass(psiClass) != isStatic
  }
}
