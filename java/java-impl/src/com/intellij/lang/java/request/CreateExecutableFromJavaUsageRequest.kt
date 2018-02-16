// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.getTargetSubstitutor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.components.service
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.codeStyle.VariableKind
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

  override fun getParameters(): List<Pair<SuggestedNameInfo, ExpectedTypes>> {
    val argumentList = call.argumentList ?: return emptyList()
    val scope = call.resolveScope
    val codeStyleManager: JavaCodeStyleManager = project.service()
    return argumentList.expressions.map { expression ->
      var argType: PsiType? = RefactoringUtil.getTypeByExpression(expression)
      val names = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, expression, argType)
      if (argType == null || PsiType.NULL == argType || LambdaUtil.notInferredType(argType)) {
        argType = PsiType.getJavaLangObject(psiManager, scope)
      }
      else if (argType is PsiDisjunctionType) {
        argType = argType.leastUpperBound
      }
      else if (argType is PsiWildcardType) {
        argType = if (argType.isBounded) argType.bound else PsiType.getJavaLangObject(psiManager, scope)
      }
      val expectedTypeInfo = argType?.let { expectedType(it, ExpectedType.Kind.SUPERTYPE) }
      val expectedTypes = expectedTypeInfo?.let { listOf(it) } ?: emptyList()
      Pair<SuggestedNameInfo, ExpectedTypes>(names, expectedTypes)
    }
  }

  val context get() = call.parentOfType(PsiMethod::class, PsiClass::class)
}
