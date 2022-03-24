// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression

interface InplaceExtractMethodProvider {
  fun extract(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): Pair<PsiMethod, PsiMethodCallExpression>
  fun extractInDialog(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean)
  fun postprocess(editor: Editor, method: PsiMethod){}
}