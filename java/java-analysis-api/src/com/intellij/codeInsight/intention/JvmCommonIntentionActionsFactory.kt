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
package com.intellij.codeInsight.intention

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UParameter

/**
 * Extension Point provides language-abstracted code modifications for JVM-based languages.
 *
 * Each method should return nullable code modification ([IntentionAction]) or list of code modifications which could be empty.
 * If method returns `null` or empty list this means that operation on given elements is not supported or not yet implemented for a language.
 *
 * Every new added method should return `null` or empty list by default and then be overridden in implementations for each language if it is possible.
 *
 * @since 2017.2
 */
@ApiStatus.Experimental
@Deprecated("to be removed in 2018.1", ReplaceWith("com.intellij.lang.jvm.actions.JvmCommonIntentionActionsFactory"))
abstract class JvmCommonIntentionActionsFactory {

  open fun createChangeModifierAction(declaration: UDeclaration,
                                      @PsiModifier.ModifierConstant modifier: String,
                                      shouldPresent: Boolean): IntentionAction? = null

  open fun createAddCallableMemberActions(info: MethodInsertionInfo): List<IntentionAction> = emptyList()

  open fun createAddBeanPropertyActions(uClass: UClass,
                                        propertyName: String,
                                        @PsiModifier.ModifierConstant visibilityModifier: String,
                                        propertyType: PsiType,
                                        setterRequired: Boolean,
                                        getterRequired: Boolean): List<IntentionAction> = emptyList()

  companion object : LanguageExtension<JvmCommonIntentionActionsFactory>(
    "com.intellij.codeInsight.intention.jvmCommonIntentionActionsFactory") {

    @JvmStatic
    override fun forLanguage(l: Language): JvmCommonIntentionActionsFactory? = super.forLanguage(l)
  }

}

@Deprecated("to be removed in 2018.1", ReplaceWith("com.intellij.lang.jvm.actions.JvmCommonIntentionActionsFactory"))
sealed class MethodInsertionInfo(
  val containingClass: UClass,
  @PsiModifier.ModifierConstant
  val modifiers: List<String> = emptyList(),
  val typeParams: List<PsiTypeParameter> = emptyList(),
  val parameters: List<UParameter> = emptyList()
) {
  companion object {

    @JvmStatic
    fun constructorInfo(containingClass: UClass, parameters: List<UParameter>) =
      Constructor(containingClass = containingClass, parameters = parameters)

    @JvmStatic
    fun simpleMethodInfo(containingClass: UClass,
                         methodName: String,
                         @PsiModifier.ModifierConstant modifier: String,
                         returnType: PsiType,
                         parameters: List<UParameter>) =
      Method(name = methodName,
             modifiers = listOf(modifier),
             containingClass = containingClass,
             returnType = returnType,
             parameters = parameters)
  }

  @Deprecated("to be removed in 2018.1", ReplaceWith("com.intellij.lang.jvm.actions.JvmCommonIntentionActionsFactory"))
  class Method(
    containingClass: UClass,
    val name: String,
    modifiers: List<String> = emptyList(),
    typeParams: List<PsiTypeParameter> = emptyList(),
    val returnType: PsiType,
    parameters: List<UParameter> = emptyList(),
    val isAbstract: Boolean = false
  ) : MethodInsertionInfo(containingClass, modifiers, typeParams, parameters)

  @Deprecated("to be removed in 2018.1", ReplaceWith("com.intellij.lang.jvm.actions.JvmCommonIntentionActionsFactory"))
  class Constructor(
    containingClass: UClass,
    modifiers: List<String> = emptyList(),
    typeParams: List<PsiTypeParameter> = emptyList(),
    parameters: List<UParameter> = emptyList()
  ) : MethodInsertionInfo(containingClass, modifiers, typeParams, parameters)

}