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
import com.intellij.codeInsight.intention.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.beanProperties.CreateJavaBeanPropertyFix
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.util.VisibilityUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UParameter


class JavaCommonIntentionActionsFactory : JvmCommonIntentionActionsFactory() {

  override fun createChangeModifierAction(declaration: UDeclaration, modifier: String, shouldPresent: Boolean): IntentionAction {
    return ModifierFix(declaration.modifierList, modifier, shouldPresent, false)
  }

  override fun createAddCallableMemberActions(info: MethodInsertionInfo): List<IntentionAction> {
    return when (info) {
      is MethodInsertionInfo.Method -> with(info) {
        createAddMethodAction(containingClass, name, modifiers.joinToString(" "), returnType, parameters)
          ?.let { listOf(it) } ?: emptyList()
      }

      is MethodInsertionInfo.Constructor ->
        listOf(AddConstructorFix(info.containingClass.psi, info.parameters.map { it.psi }))
    }
  }

  private fun createAddMethodAction(uClass: UClass,
                                    methodName: String,
                                    @PsiModifier.ModifierConstant @NotNull visibilityModifier: String,
                                    returnType: PsiType,
                                    parameters: List<UParameter>): IntentionAction? {
    val paramsString = parameters.mapIndexed { i, t -> "${t.type.presentableText} ${t.name ?: "arg$i"}" }.joinToString()
    val signatureString =
      "${VisibilityUtil.getVisibilityString(visibilityModifier)} ${returnType.presentableText} $methodName($paramsString){}"
    val targetClassPointer = SmartPointerManager.getInstance(uClass.project).createSmartPsiElementPointer(uClass.psi)
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
        targetClassPointer.element?.let { targetClass ->
          targetClass.add(createMethod(targetClass))
        }
      }

      private fun createMethod(targetClass: PsiClass): PsiMethod = PsiElementFactory.SERVICE.getInstance(targetClass.project)
        .createMethodFromText(signatureString, targetClass)
    }
  }

  override fun createAddBeanPropertyActions(uClass: UClass,
                                            propertyName: String,
                                            @PsiModifier.ModifierConstant visibilityModifier: String,
                                            propertyType: PsiType,
                                            setterRequired: Boolean,
                                            getterRequired: Boolean): List<IntentionAction> {
    if (getterRequired && setterRequired)
      return listOf<IntentionAction>(
        CreateJavaBeanPropertyFix(uClass.psi, propertyName, propertyType, getterRequired, setterRequired,
                                  true),
        CreateJavaBeanPropertyFix(uClass.psi, propertyName, propertyType, getterRequired, setterRequired,
                                  false))
    if (getterRequired || setterRequired)
      return listOf<IntentionAction>(
        CreateJavaBeanPropertyFix(uClass.psi, propertyName, propertyType, getterRequired, setterRequired,
                                  true),
        CreateJavaBeanPropertyFix(uClass.psi, propertyName, propertyType, getterRequired, setterRequired,
                                  false),
        CreateJavaBeanPropertyFix(uClass.psi, propertyName, propertyType, true, true, true))

    return listOf<IntentionAction>(
      CreateJavaBeanPropertyFix(uClass.psi, propertyName, propertyType, getterRequired, setterRequired, true))
  }

  override fun createAddAnnotationAction(declaration: UDeclaration, annotationInfo: AnnotationInsertionInfo): IntentionAction? {
    val modifierList = declaration.psi.modifierList ?: return null

    val modifierListPointer = SmartPointerManager.getInstance(modifierList.project).createSmartPsiElementPointer(modifierList)

    return object : AbstractIntentionAction() {
      override fun getText() = QuickFixBundle.message("add.annotation.to.declaration",
                                                      StringUtilRt.getShortName(annotationInfo.qualifiedName))

      override fun startInWriteAction(): Boolean = true

      override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val modifierList = modifierListPointer.element ?: return;
        val annotation = modifierList.addAnnotation(annotationInfo.qualifiedName)
        val support = LanguageAnnotationSupport.INSTANCE.forLanguage(annotation.language)
        for ((name, value) in annotationInfo.literalValues) {
          annotation.setDeclaredAttributeValue(name, support
            .createLiteralValue(value, annotation))
        }
        val formatter = CodeStyleManager.getInstance(project)
        val codeStyleManager = JavaCodeStyleManager.getInstance(project)
        codeStyleManager.shortenClassReferences(formatter.reformat(annotation))

      }

    }

  }
}