// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures

import com.intellij.codeInsight.Nullability
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable

sealed class DataOutput {
  abstract val type: PsiType
  abstract val nullability: Nullability

  data class VariableOutput(override val type: PsiType, val variable: PsiVariable, val declareType: Boolean,
                            override val nullability: Nullability = Nullability.UNKNOWN) : DataOutput() {
    val name: String = requireNotNull(variable.name)
  }

  data class ExpressionOutput(override val type: PsiType, val name: String?, val returnExpressions: List<PsiExpression>,
                              override val nullability: Nullability = Nullability.UNKNOWN) : DataOutput()

  object ArtificialBooleanOutput : DataOutput() {
    override val type: PsiType = PsiType.BOOLEAN
    override val nullability: Nullability = Nullability.UNKNOWN
  }

  data class EmptyOutput(override val type: PsiType = PsiType.VOID) : DataOutput(){
    override val nullability: Nullability = Nullability.UNKNOWN
  }

  fun withType(type: PsiType): DataOutput {
    return when(this) {
      is VariableOutput -> this.copy(type = type)
      is ExpressionOutput -> this.copy(type = type)
      is EmptyOutput -> this.copy(type = type)
      ArtificialBooleanOutput -> this
    }
  }
}