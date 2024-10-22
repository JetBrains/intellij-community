// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import java.util.regex.Pattern

internal class JavaEvaluationContextWrapper : EvaluationContextWrapper {
  override fun wrapContext(project: Project, context: PsiElement?, additionalElements: List<AdditionalContextElement>): PsiElement? {
    if (additionalElements.isEmpty()) return context
    val elementsByName = additionalElements.groupBy { it.name }.mapValues { (_, v) -> v[0] }
    val text = additionalElements.joinToString("\n") { (name, _, jvmTypeName, _) ->
      val suitableTypeName = convertTypeName(jvmTypeName)
      "$suitableTypeName $name;"
    }
    val fragment = JavaPsiFacade.getElementFactory(project).createCodeBlockFromText("{$text}", context)
    fragment.accept(object : JavaRecursiveElementVisitor() {
      override fun visitLocalVariable(variable: PsiLocalVariable) {
        val name = variable.name
        val element = elementsByName[name] ?: return
        variable.putUserData(AdditionalContextProvider.ADDITIONAL_CONTEXT_ELEMENT_KEY, element)
      }
    })
    return fragment
  }
}

private val ANONYMOUS_CLASS_NAME_PATTERN = Pattern.compile(".*\\$\\d.*")

// TODO: we probably need something more complicated for type name generation, but not in EDT
private fun convertTypeName(typeName: String): String =
  if (ANONYMOUS_CLASS_NAME_PATTERN.matcher(typeName).matches() || DebuggerUtilsEx.isLambdaClassName(typeName)) {
    "Object"
  }
  else {
    typeName.replace('$', '.')
  }
