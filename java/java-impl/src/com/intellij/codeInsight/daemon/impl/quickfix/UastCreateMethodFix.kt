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
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory
import com.intellij.codeInsight.intention.MethodInsertionInfo
import com.intellij.codeInspection.LocalQuickFixBase
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class UastCreateMethodFix(containingClass: UClass, private val createMethodAction: IntentionAction)
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

  private fun reformatAndOpenCreatedMethod(method: UMethod) {
    CodeStyleManager.getInstance(containingClass.project).reformat(method)
    PsiNavigateUtil.navigate(method.uastBody?.psi?.lastChild ?: method)
  }

  companion object {
    @JvmStatic
    fun createVoidMethodIfFixPossible(uClass: UClass,
                                      methodName: String,
                                      @PsiModifier.ModifierConstant modifier: String): UastCreateMethodFix? {
      if (!ModuleUtilCore.projectContainsFile(uClass.project, uClass.containingFile.virtualFile, false)) return null
      val actionsFactory = JvmCommonIntentionActionsFactory.forLanguage(uClass.language) ?: return null
      val action = actionsFactory.createAddCallableMemberActions(
        MethodInsertionInfo.simpleMethodInfo(uClass, methodName, modifier, PsiType.VOID, emptyList())
      ).firstOrNull() ?: return null
      return UastCreateMethodFix(uClass, action)
    }
  }

}

