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
import com.intellij.codeInsight.daemon.impl.quickfix.AddConstructorFix
import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix
import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory
import com.intellij.codeInsight.intention.MethodInsertionInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.beanProperties.CreateJavaBeanPropertyFix
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.util.VisibilityUtil
import org.jetbrains.annotations.NotNull

class JavaCommonIntentionActionsFactory : JvmCommonIntentionActionsFactory() {

  override fun createChangeModifierAction(declaration: @JvmCommon PsiModifierListOwner,
                                          modifier: String,
                                          shouldPresent: Boolean): IntentionAction {
    return ModifierFix(declaration.modifierList, modifier, shouldPresent, false)
  }

  override fun createAddCallableMemberActions(info: MethodInsertionInfo): List<IntentionAction> {
    return when (info) {
      is MethodInsertionInfo.Method -> with(info) {
        createAddMethodAction(targetClass, name, modifiers.joinToString(" "), returnType, parameters)
          ?.let { listOf(it) } ?: emptyList()
      }

      is MethodInsertionInfo.Constructor ->
        listOf(AddConstructorFix(info.targetClass.javaPsi(), info.parameters.map { it.javaPsi() }))
    }
  }

  private fun createAddMethodAction(psiClass: @JvmCommon PsiClass,
                                    methodName: String,
                                    @PsiModifier.ModifierConstant @NotNull visibilityModifier: String,
                                    returnType: PsiType,
                                    parameters: List<@JvmCommon PsiParameter>): IntentionAction? {
    val paramsString = parameters.mapIndexed { i, t -> "${t.type.canonicalText} ${t.name ?: "arg$i"}" }.joinToString()
    val signatureString =
      "${VisibilityUtil.getVisibilityString(visibilityModifier)} ${returnType.canonicalText} $methodName($paramsString){}"
    val targetClassPointer = SmartPointerManager.getInstance(psiClass.project).createSmartPsiElementPointer(psiClass.javaPsi())
    return object : AbstractIntentionAction() {

      private val text = targetClassPointer.element?.let { psiClass ->
        QuickFixBundle.message("create.method.from.usage.text",
                               PsiFormatUtil.formatMethod(createMethod(psiClass), PsiSubstitutor.EMPTY,
                                                          PsiFormatUtilBase.SHOW_NAME or
                                                            PsiFormatUtilBase.SHOW_TYPE or
                                                            PsiFormatUtilBase.SHOW_PARAMETERS or
                                                            PsiFormatUtilBase.SHOW_RAW_TYPE,
                                                          PsiFormatUtilBase.SHOW_TYPE or PsiFormatUtilBase.SHOW_RAW_TYPE, 2))
      } ?: ""

      override fun getText(): String = text

      override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val targetClass = targetClassPointer.element ?: return
        runWriteAction {
          val addedMethod = targetClass.add(createMethod(targetClass))
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedMethod)
        }
      }

      private fun createMethod(targetClass: PsiClass): PsiMethod {
        val elementFactory = JVMElementFactories.getFactory(targetClass.language, targetClass.project) // it could be Groovy
                             ?: JavaPsiFacade.getElementFactory(targetClass.project)
        return elementFactory.createMethodFromText(signatureString, targetClass)
      }
    }
  }

  private inline fun <reified T : PsiElement> T.javaPsi(): T {
    assert(language == JavaLanguage.INSTANCE)
    return when (this) {
      is org.jetbrains.uast.UElement -> psi as T
      else -> this
    }
  }

  override fun createAddBeanPropertyActions(psiClass: @JvmCommon PsiClass,
                                            propertyName: String,
                                            @PsiModifier.ModifierConstant visibilityModifier: String,
                                            propertyType: PsiType,
                                            setterRequired: Boolean,
                                            getterRequired: Boolean): List<IntentionAction> {
    if (getterRequired && setterRequired)
      return listOf<IntentionAction>(
        CreateJavaBeanPropertyFix(psiClass.javaPsi(), propertyName, propertyType, getterRequired, setterRequired,
                                  true),
        CreateJavaBeanPropertyFix(psiClass.javaPsi(), propertyName, propertyType, getterRequired, setterRequired,
                                  false))
    if (getterRequired || setterRequired)
      return listOf<IntentionAction>(
        CreateJavaBeanPropertyFix(psiClass.javaPsi(), propertyName, propertyType, getterRequired, setterRequired,
                                  true),
        CreateJavaBeanPropertyFix(psiClass.javaPsi(), propertyName, propertyType, getterRequired, setterRequired,
                                  false),
        CreateJavaBeanPropertyFix(psiClass.javaPsi(), propertyName, propertyType, true, true, true))

    return listOf<IntentionAction>(
      CreateJavaBeanPropertyFix(psiClass.javaPsi(), propertyName, propertyType, getterRequired, setterRequired, true))
  }

}