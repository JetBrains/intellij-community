// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.getTargetSubstitutor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.components.service
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.CommonJavaRefactoringUtil

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
      val argType: PsiType? = CommonJavaRefactoringUtil.getTypeByExpression(expression)
      val type = CreateFromUsageUtils.getParameterTypeByArgumentType(argType, psiManager, scope)
      val names = codeStyleManager.suggestSemanticNames(expression)
      val expectedTypes = expectedTypes(type, ExpectedType.Kind.SUPERTYPE)
      expectedParameter(expectedTypes, names)
    }
  }

  val context get() = call.parentOfTypes(PsiMethod::class, PsiClass::class)
}
