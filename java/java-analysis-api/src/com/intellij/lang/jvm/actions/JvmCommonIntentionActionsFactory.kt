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
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.components.ServiceManager
import org.jetbrains.annotations.ApiStatus

/**
 * Extension Point provides language-abstracted code modifications for JVM-based languages.
 *
 * Each method should return nullable code modification ([IntentionAction]) or list of code modifications which could be empty.
 * If method returns `null` or empty list this means that operation on given elements is not supported or not yet implemented for a language.
 *
 * Every new added method should return `null` or empty list by default and then be overridden in implementations for each language if it is possible.
 *
 * @since 2017.3
 */
@ApiStatus.Experimental
abstract class JvmCommonIntentionActionsFactory {

  open fun createChangeJvmModifierAction(declaration: JvmModifiersOwner,
                                         modifier: JvmModifier,
                                         shouldPresent: Boolean): IntentionAction? = null

  open fun createAddCallableMemberActions(info: MethodInsertionInfo): List<IntentionAction> = emptyList()

  open fun createAddJvmPropertyActions(psiClass: JvmClass,
                                       propertyName: String,
                                       visibilityModifier: JvmModifier,
                                       propertyType: JvmType,
                                       setterRequired: Boolean,
                                       getterRequired: Boolean): List<IntentionAction> = emptyList()

  companion object : LanguageExtension<JvmCommonIntentionActionsFactory>(
    "com.intellij.lang.jvm.actions.jvmCommonIntentionActionsFactory") {

    @JvmStatic
    override fun forLanguage(l: Language): JvmCommonIntentionActionsFactory? =
      super.forLanguage(l)
      ?: ServiceManager.getService(JvmCommonIntentionActionsFactoryFallback::class.java).forLanguage(l)
  }

}

@ApiStatus.Experimental
sealed class MethodInsertionInfo(
  val targetClass: JvmClass,
  val modifiers: List<JvmModifier> = emptyList(),
  val typeParameters: List<JvmTypeParameter> = emptyList(),
  val parameters: List<JvmParameter> = emptyList()
) {


  companion object {

    @JvmStatic
    fun constructorInfo(targetClass: JvmClass, parameters: List<JvmParameter>) =
      Constructor(targetClass = targetClass, parameters = parameters)

    @JvmStatic
    fun simpleMethodInfo(containingClass: JvmClass,
                         methodName: String,
                         modifier: List<JvmModifier>,
                         returnType: JvmType,
                         parameters: List<JvmParameter>) =
      Method(name = methodName,
             modifiers = modifier,
             targetClass = containingClass,
             returnType = returnType,
             parameters = parameters)

    @JvmStatic
    fun simpleMethodInfo(containingClass: JvmClass,
                         methodName: String,
                         modifier: JvmModifier,
                         returnType: JvmType,
                         parameters: List<JvmParameter>) =
      simpleMethodInfo(containingClass, methodName, listOf(modifier), returnType, parameters)


  }

  class Method(
    targetClass: JvmClass,
    val name: String,
    modifiers: List<JvmModifier> = emptyList(),
    typeParams: List<JvmTypeParameter> = emptyList(),
    val returnType: JvmType,
    parameters: List<JvmParameter> = emptyList(),
    val isAbstract: Boolean = false
  ) : MethodInsertionInfo(targetClass, modifiers, typeParams, parameters)

  class Constructor(
    targetClass: JvmClass,
    modifiers: List<JvmModifier> = emptyList(),
    typeParams: List<JvmTypeParameter> = emptyList(),
    parameters: List<JvmParameter> = emptyList()
  ) : MethodInsertionInfo(targetClass, modifiers, typeParams, parameters)

}

