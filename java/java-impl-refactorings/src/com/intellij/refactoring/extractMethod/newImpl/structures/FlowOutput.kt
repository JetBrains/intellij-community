// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures

import com.intellij.psi.PsiStatement

sealed class FlowOutput {
  abstract val statements: List<PsiStatement>

  data class ConditionalFlow(override val statements: List<PsiStatement>) : FlowOutput()

  data class UnconditionalFlow(override val statements: List<PsiStatement>, val isDefaultExit: Boolean) : FlowOutput()

  object EmptyFlow : FlowOutput() {
    override val statements: List<PsiStatement> = emptyList()
  }
}