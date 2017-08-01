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
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import com.intellij.lang.jvm.actions.MemberRequest
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

class CreateMethodFix(containingClass: @JvmCommon PsiClass, private val createMethodAction: IntentionAction)
  : LocalQuickFixBase(createMethodAction.text, createMethodAction.familyName) {

  private val containingClass = SmartPointerManager.getInstance(containingClass.project).createSmartPsiElementPointer(containingClass)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = containingClass.containingFile ?: return
    val methodsBefore = containingClass.element?.methods ?: return
    createMethodAction.invoke(project, null, file)
    val newMethod = containingClass.element?.methods?.
      firstOrNull { method -> methodsBefore.none { it.isEquivalentTo(method) } } ?: return
    reformatAndOpenCreatedMethod(newMethod)
  }

  private fun reformatAndOpenCreatedMethod(method: @JvmCommon PsiMethod) {
    CodeStyleManager.getInstance(containingClass.project).reformat(method)
    PsiNavigateUtil.navigate((method.body ?: uastBody(method))?.lastChild ?: method)
  }

  private fun uastBody(method: PsiMethod): PsiElement? = when (method) {
    is UMethod -> method.uastBody?.psi
    else -> method.toUElementOfType<UMethod>()?.uastBody?.psi
  }

  companion object {
    @JvmStatic
    fun createVoidMethodIfFixPossible(psiClass: @JvmCommon PsiClass,
                                      methodName: String,
                                      modifier: JvmModifier): CreateMethodFix? {
      if (!ModuleUtilCore.projectContainsFile(psiClass.project, psiClass.containingFile.virtualFile, false)) return null
      val actionsFactory = JvmElementActionsFactory.forLanguage(psiClass.language) ?: return null
      val action = actionsFactory.createActions(psiClass,
                                                MemberRequest.simpleMethodRequest(methodName, modifier, PsiType.VOID, emptyList())
      ).firstOrNull() ?: return null
      return CreateMethodFix(psiClass, action)
    }
  }

}

