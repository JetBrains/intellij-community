// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.codeStyle.VariableKind

fun nameInfo(vararg names: String): SuggestedNameInfo = object : SuggestedNameInfo(names) {}

fun suggestJavaParamName(project: Project, type: PsiType, propertyName: String? = null): SuggestedNameInfo {
  val codeStyleManager = JavaCodeStyleManager.getInstance(project)!!
  return codeStyleManager.suggestVariableName(VariableKind.PARAMETER, propertyName, null, type)
}

fun SuggestedNameInfo.orDefault(defaultName: String): SuggestedNameInfo {
  return if (names.isEmpty()) nameInfo(defaultName) else this
}
