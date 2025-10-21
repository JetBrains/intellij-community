// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.getTargetSubstitutor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.CommonJavaRefactoringUtil

public abstract class CreateExecutableFromJavaUsageRequest<out T : PsiCall>(
  call: T,
  private val modifiers: Collection<JvmModifier>
) : CreateExecutableRequest {

  private val psiManager = call.manager
  private val project = psiManager.project
  private val callPointer: SmartPsiElementPointer<T> = call.createSmartPointer(project)
  public val call: T get() = callPointer.element ?: error("dead pointer")

  override fun isValid(): Boolean = callPointer.element != null

  override fun getAnnotations(): List<AnnotationRequest> = emptyList()

  override fun getModifiers(): Collection<JvmModifier> = modifiers

  override fun getTargetSubstitutor(): PsiJvmSubstitutor = PsiJvmSubstitutor(project, getTargetSubstitutor(call))

  override fun getExpectedParameters(): List<ExpectedParameter> {
    val argumentList = call.argumentList ?: return emptyList()
    val scope = call.resolveScope
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)
    return argumentList.expressions.map { expression ->
      val argType: PsiType? = CommonJavaRefactoringUtil.getTypeByExpression(expression)
      val type = CreateFromUsageUtils.getParameterTypeByArgumentType(argType, psiManager, scope)
      val names = codeStyleManager.suggestSemanticNames(expression, VariableKind.PARAMETER)
      val expectedTypes = expectedTypes(type, ExpectedType.Kind.SUPERTYPE)
      expectedParameter(expectedTypes, names)
    }
  }

  public val context: PsiElement? get() = call.parentOfTypes(PsiMethod::class, PsiClass::class)
}
