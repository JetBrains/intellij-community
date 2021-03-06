// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ml.local

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

internal object JavaLocalModelsUtil {

  fun getMethodName(method: PsiMethod): String? = method.presentation?.presentableText

  fun getClassName(cls: PsiClass): String? = cls.qualifiedName
}