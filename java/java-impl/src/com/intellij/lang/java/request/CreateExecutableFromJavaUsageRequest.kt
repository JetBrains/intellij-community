// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.getTargetSubstitutor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.components.service
import com.intellij.psi.*
import com.intellij.psi.PsiType.getJavaLangObject
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.util.RefactoringUtil

internal abstract class CreateExecutableFromJavaUsageRequest<out T : PsiCall>(
  call: T,
  private val modifiers: Collection<JvmModifier>
) : CreateExecutableRequest {

  private val psiManager = call.manager
  private val project = psiManager.project
  private val callPointer: SmartPsiElementPointer<T> = call.createSmartPointer(project)
  protected val call: T get() = callPointer.element ?: error("dead pointer")

  override fun isValid() = callPointer.element != null

  override fun getAnnotations() = emptyList<AnnotationRequest>()

  override fun getModifiers() = modifiers

  override fun getTargetSubstitutor() = PsiJvmSubstitutor(project, getTargetSubstitutor(call))

  override fun getExpectedParameters(): List<ExpectedParameter> {
    val argumentList = call.argumentList ?: return emptyList()
    val scope = call.resolveScope
    val codeStyleManager: JavaCodeStyleManager = project.service()
    return argumentList.expressions.map { expression ->
      val type = getArgType(expression, scope)
      val names = codeStyleManager.suggestSemanticNames(expression)
      val expectedTypes = if (type == null) emptyList() else expectedTypes(type, ExpectedType.Kind.SUPERTYPE)
      expectedParameter(expectedTypes, names)
    }
  }

  override fun getParameters() = getParameters(expectedParameters, project)

  private fun getArgType(expression: PsiExpression, scope: GlobalSearchScope): PsiType? {
    val argType: PsiType? = RefactoringUtil.getTypeByExpression(expression)
    if (argType == null || PsiType.NULL == argType || LambdaUtil.notInferredType(argType)) {
      return getJavaLangObject(psiManager, scope)
    }
    else if (argType is PsiDisjunctionType) {
      return argType.leastUpperBound
    }
    else if (argType is PsiWildcardType) {
      return if (argType.isBounded) argType.bound else getJavaLangObject(psiManager, scope)
    }
    return argType
  }

  val context get() = call.parentOfType(PsiMethod::class, PsiClass::class)
}
