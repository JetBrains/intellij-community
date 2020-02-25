// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable

sealed class DataOutput {
  abstract val type: PsiType

  data class VariableOutput(override val type: PsiType, val variable: PsiVariable, val declareType: Boolean) : DataOutput() {
    val name: String = requireNotNull(variable.name)
  }

  data class ExpressionOutput(override val type: PsiType, val name: String, val returnExpressions: List<PsiExpression>) : DataOutput()

  object ArtificialBooleanOutput : DataOutput() {
    override val type: PsiType = PsiType.BOOLEAN
  }

  object EmptyOutput : DataOutput() {
    override val type: PsiType = PsiType.VOID
  }
}