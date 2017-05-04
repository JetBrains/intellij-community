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
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix
import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.util.VisibilityUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration


class JavaCommonIntentionActionsFactory : JvmCommonIntentionActionsFactory() {

  override fun createChangeModifierAction(declaration: UDeclaration, modifier: String, shouldPresent: Boolean): IntentionAction {
    return ModifierFix(declaration.modifierList, modifier, shouldPresent, false)
  }

  override fun createAddMethodAction(u: UClass,
                                     methodName: String,
                                     @PsiModifier.ModifierConstant @NotNull visibilityModifier: String,
                                     returnType: PsiType,
                                     vararg parameters: PsiType): IntentionAction? {
    val paramsString = parameters.mapIndexed { i, t -> "${t.presentableText} arg$i" }.joinToString()
    val signatureString =
      "${VisibilityUtil.getVisibilityString(visibilityModifier)} ${returnType.presentableText} $methodName($paramsString){}"
    val smartPsi = SmartPointerManager.getInstance(u.project).createSmartPsiElementPointer(u.psi)
    return object : AbstractIntentionAction() {

      private val text = QuickFixBundle.message("add.method.text", methodName, u.name)

      override fun getText(): String = text

      override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val psi = smartPsi.element ?: return
        val createMethodFromText = PsiElementFactory.SERVICE.getInstance(u.project)
          .createMethodFromText(signatureString, psi)
        psi.add(createMethodFromText)
      }
    }
  }
}