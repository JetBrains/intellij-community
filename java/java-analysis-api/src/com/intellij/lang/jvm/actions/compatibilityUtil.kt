// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CompatibilityUtil")

package com.intellij.lang.jvm.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.codeStyle.VariableKind

internal fun getParameters(expectedParameters: List<ExpectedParameter>): List<Pair<SuggestedNameInfo, List<ExpectedType>>> {
  return expectedParameters.map {
    val nameInfo = object : SuggestedNameInfo(it.semanticNames.toTypedArray()) {}
    Pair(nameInfo, it.expectedTypes)
  }
}

fun getParameters(expectedParameters: List<ExpectedParameter>, project: Project): List<Pair<SuggestedNameInfo, List<ExpectedType>>> {
  val styleManager = JavaCodeStyleManager.getInstance(project)
  return expectedParameters.map {
    val expectedTypes = it.expectedTypes
    val nameInfo = styleManager.suggestNames(it.semanticNames, VariableKind.PARAMETER, expectedTypes.firstOrNull()?.theType as? PsiType)
    Pair(nameInfo, expectedTypes)
  }
}
