// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.PsiVariable

data class ExtractOptions(
  val anchor: PsiElement,
  val elements: List<PsiElement>,
  val flowOutput: FlowOutput,
  val dataOutput: DataOutput,
  val thrownExceptions: List<PsiClassType>,
  val requiredVariablesInside: List<PsiVariable>,
  val inputParameters: List<InputParameter>,
  val typeParameters: PsiTypeParameterList,
  val methodName: String,
  val isStatic: Boolean,
  val visibility: String,
  val exposedLocalVariables: List<PsiVariable>,
  val isConstructor: Boolean
) {
  val project: Project
    get() = elements.first().project
}

