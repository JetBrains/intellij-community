// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiType

data class InputParameter(val references: List<PsiExpression>,
                          val name: String,
                          val type: PsiType,
                          val annotations: List<PsiAnnotation> = emptyList()) {
  init {
    require(references.isNotEmpty())
  }
}