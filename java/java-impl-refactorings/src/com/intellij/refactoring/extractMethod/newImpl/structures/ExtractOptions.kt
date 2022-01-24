// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures

import com.intellij.openapi.project.Project
import com.intellij.psi.*

data class ExtractOptions(
  val anchor: PsiMember,
  val elements: List<PsiElement>,
  val flowOutput: FlowOutput,
  val dataOutput: DataOutput,
  val thrownExceptions: List<PsiClassType> = emptyList(),
  val requiredVariablesInside: List<PsiVariable> = emptyList(),
  val inputParameters: List<InputParameter> = emptyList(),
  val typeParameters: List<PsiTypeParameter> = emptyList(),
  val methodName: String = "extracted",
  val isStatic: Boolean = false,
  val visibility: String? = PsiModifier.PRIVATE,
  val exposedLocalVariables: List<PsiVariable> = emptyList(),
  val disabledParameters: List<InputParameter> = emptyList(),
  val isConstructor: Boolean = false,
  val project: Project = anchor.project
)
