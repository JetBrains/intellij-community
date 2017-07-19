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
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastContext

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
abstract class JvmCommonIntentionActionsFactory {

  open fun createChangeModifierAction(declaration: @JvmCommon PsiModifierListOwner,
                                      @PsiModifier.ModifierConstant modifier: String,
                                      shouldPresent: Boolean): IntentionAction? =
    //Fallback if Uast-version of method is overridden
    createChangeModifierAction(declaration.asUast<UDeclaration>(), modifier, shouldPresent)

  open fun createAddCallableMemberActions(info: MethodInsertionInfo): List<IntentionAction> = emptyList()

  open fun createGenerateFieldFromUsageActions(info: CreateFromUsage.FieldInfo): List<IntentionAction> = emptyList()
  open fun createGenerateMethodFromUsageActions(info: CreateFromUsage.MethodInfo): List<IntentionAction> = emptyList()
  open fun createGenerateConstructorFromUsageActions(info: CreateFromUsage.ConstructorInfo): List<IntentionAction> = emptyList()

  open fun createAddBeanPropertyActions(psiClass: @JvmCommon PsiClass,
                                        propertyName: String,
                                        @PsiModifier.ModifierConstant visibilityModifier: String,
                                        propertyType: PsiType,
                                        setterRequired: Boolean,
                                        getterRequired: Boolean): List<IntentionAction> =
    //Fallback if Uast-version of method is overridden
    createAddBeanPropertyActions(psiClass.asUast<UClass>(), propertyName, visibilityModifier, propertyType, setterRequired, getterRequired)

  companion object : LanguageExtension<JvmCommonIntentionActionsFactory>(
    "com.intellij.codeInsight.intention.jvmCommonIntentionActionsFactory") {

    @JvmStatic
    override fun forLanguage(l: Language): JvmCommonIntentionActionsFactory? = super.forLanguage(l)
  }

  //A fallback to old api
  @Deprecated("use or/and override @JvmCommon-version of this method instead")
  open fun createChangeModifierAction(declaration: UDeclaration,
                                      @PsiModifier.ModifierConstant modifier: String,
                                      shouldPresent: Boolean): IntentionAction? = null

  @Deprecated("use or/and override @JvmCommon-version of this method instead")
  open fun createAddBeanPropertyActions(uClass: UClass,
                                        propertyName: String,
                                        @PsiModifier.ModifierConstant visibilityModifier: String,
                                        propertyType: PsiType,
                                        setterRequired: Boolean,
                                        getterRequired: Boolean): List<IntentionAction> = emptyList()


}

@ApiStatus.Experimental
object CreateFromUsage {
  // type constraint is a language-specific object (e.g. ExpectedTypeInfo for Java)
  class TypeInfo(val typeConstraints: List<Any>)

  class ParameterInfo(val typeInfo: TypeInfo, val suggestedNames: List<String>)

  abstract class MemberInfo(
      val targetClass: @JvmCommon PsiClass,
      @PsiModifier.ModifierConstant val modifiers: List<String> = emptyList()
  )

  class FieldInfo(
      targetClass: @JvmCommon PsiClass,
      val name: String,
      @PsiModifier.ModifierConstant
      modifiers: List<String> = emptyList(),
      val returnType: TypeInfo
  ) : MemberInfo(targetClass, modifiers)

  class MethodInfo(
      targetClass: @JvmCommon PsiClass,
      val name: String,
      @PsiModifier.ModifierConstant
      modifiers: List<String> = emptyList(),
      val returnType: TypeInfo,
      val parameters: List<ParameterInfo>
  ) : MemberInfo(targetClass, modifiers)

  class ConstructorInfo(
      targetClass: @JvmCommon PsiClass,
      @PsiModifier.ModifierConstant
      modifiers: List<String> = emptyList(),
      val parameters: List<ParameterInfo>
  ) : MemberInfo(targetClass, modifiers)
}

@ApiStatus.Experimental
sealed class MethodInsertionInfo(
  val targetClass: @JvmCommon PsiClass,
  @PsiModifier.ModifierConstant
  val modifiers: List<String> = emptyList(),
  val typeParams: List<PsiTypeParameter> = emptyList(),
  val parameters: List<@JvmCommon PsiParameter> = emptyList()
) {

  @Deprecated("use `targetClass`", ReplaceWith("targetClass"))
  val containingClass: UClass
    get() = targetClass.asUast<UClass>()

  companion object {

    @JvmStatic
    fun constructorInfo(targetClass: @JvmCommon PsiClass, parameters: List<@JvmCommon PsiParameter>) =
      Constructor(targetClass = targetClass, parameters = parameters)

    @JvmStatic
    fun simpleMethodInfo(containingClass: @JvmCommon PsiClass,
                         methodName: String,
                         @PsiModifier.ModifierConstant modifier: String,
                         returnType: PsiType,
                         parameters: List<@JvmCommon PsiParameter>) =
      Method(name = methodName,
             modifiers = listOf(modifier),
             targetClass = containingClass,
             returnType = returnType,
             parameters = parameters)

  }

  class Method(
    targetClass: @JvmCommon PsiClass,
    val name: String,
    modifiers: List<String> = emptyList(),
    typeParams: List<@JvmCommon PsiTypeParameter> = emptyList(),
    val returnType: PsiType,
    parameters: List<@JvmCommon PsiParameter> = emptyList(),
    val isAbstract: Boolean = false
  ) : MethodInsertionInfo(targetClass, modifiers, typeParams, parameters)

  class Constructor(
    targetClass: @JvmCommon PsiClass,
    modifiers: List<String> = emptyList(),
    typeParams: List<@JvmCommon PsiTypeParameter> = emptyList(),
    parameters: List<@JvmCommon PsiParameter> = emptyList()
  ) : MethodInsertionInfo(targetClass, modifiers, typeParams, parameters)

}

@Deprecated("remove after kotlin plugin will be ported")
private inline fun <reified T : UElement> PsiElement.asUast(): T = when (this) {
  is T -> this
  else -> this.let { ServiceManager.getService(project, UastContext::class.java).convertElement(this, null, T::class.java) as T? }
          ?: throw UnsupportedOperationException("cant convert $this to ${T::class}")
}