// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression

interface InplaceExtractMethodProvider {
  fun extract(parameters: ExtractParameters): Pair<PsiMethod, PsiMethodCallExpression>
  fun extractInDialog(parameters: ExtractParameters)
  fun postprocess(editor: Editor, method: PsiMethod){}
}

data class ExtractParameters(val targetClass: PsiClass, val range: TextRange, val methodName: String, val annotate: Boolean, val static: Boolean)