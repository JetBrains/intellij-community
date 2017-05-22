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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration

/**
 * Extension Point provides language-abstracted code modifications for JVM-based languages.
 *
 * Each method should return nullable code modification ([IntentionAction]).
 * If method returns `null` this means that operation on given elements is not supported or not yet implemented for a language.
 *
 * Every new added method should return `null` by default and then be overridden in implementations for each language if it is possible.
 *
 * @since 2017.2
 */
@ApiStatus.Experimental
abstract class JvmCommonIntentionActionsFactory {

  open fun createChangeModifierAction(declaration: UDeclaration,
                                      @PsiModifier.ModifierConstant @NonNls modifier: String,
                                      shouldPresent: Boolean): IntentionAction? = null

  open fun createAddMethodAction(uClass: UClass,
                                 methodName: String,
                                 @PsiModifier.ModifierConstant visibilityModifier: String,
                                 returnType: PsiType,
                                 vararg parameters: PsiType): IntentionAction? = null

  open fun createAddBeanPropertyActions(uClass: UClass,
                                        propertyName: String,
                                        @PsiModifier.ModifierConstant visibilityModifier: String,
                                        propertyType: PsiType,
                                        setterRequired: Boolean,
                                        getterRequired: Boolean): Array<IntentionAction> = emptyArray()

  companion object : LanguageExtension<JvmCommonIntentionActionsFactory>(
    "com.intellij.codeInsight.intention.jvmCommonIntentionActionsFactory") {

    @JvmStatic
    override fun forLanguage(l: Language): JvmCommonIntentionActionsFactory? = super.forLanguage(l)
  }
}

