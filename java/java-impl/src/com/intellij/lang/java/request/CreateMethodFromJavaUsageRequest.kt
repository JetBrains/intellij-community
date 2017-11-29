/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.guessExpectedTypes
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.getTargetSubstitutor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.hasErrorsInArgumentList
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.util.withPrevious

class CreateMethodFromJavaUsageRequest(
  methodCall: PsiMethodCallExpression,
  override val modifiers: Collection<JvmModifier>,
  override val annotations: Collection<AnnotationRequest> = emptyList()
) : CreateMethodRequest {

  private val myMethodCall = methodCall.createSmartPointer()

  override val isValid: Boolean get() {
    val methodCall = myMethodCall.element
    return methodCall?.methodExpression?.referenceName != null && !hasErrorsInArgumentList(methodCall)
  }

  private val methodCall get() = myMethodCall.element!!

  override val methodName: String get() = methodCall.methodExpression.referenceName!!

  override val returnType: ExpectedTypes get() = guessExpectedTypes(methodCall, methodCall.parent is PsiStatement).map(::ExpectedJavaType)

  override val targetSubstitutor: JvmSubstitutor get() {
    val call = methodCall
    return PsiJvmSubstitutor(call.project, getTargetSubstitutor(call))
  }

  override val parameters: List<ExpectedParameter>
    get() {
      val scope = methodCall.resolveScope
      val psiManager = methodCall.manager
      val codeStyleManager = JavaCodeStyleManager.getInstance(psiManager.project)!!
      return methodCall.argumentList.expressions.map { expression ->
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
        ExpectedParameter(names, expectedTypes)
      }
    }

  val context get() = methodCall.parentOfType(PsiMethod::class, PsiClass::class)

  private val enclosingMember get() = methodCall.parentOfType(PsiMethod::class, PsiField::class, PsiClassInitializer::class)

  fun getAnchor(targetClass: PsiClass): PsiElement? {
    val enclosingMember = enclosingMember ?: return null
    for ((parent, lastParent) in enclosingMember.parents().withPrevious()) {
      if (parent == targetClass) return lastParent
    }
    return null
  }
}
