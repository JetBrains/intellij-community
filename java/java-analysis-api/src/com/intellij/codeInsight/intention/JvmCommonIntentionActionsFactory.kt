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
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
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

  open fun createChangeJvmModifierAction(declaration: JvmModifiersOwner,
                                         modifier: JvmModifier,
                                         shouldPresent: Boolean): IntentionAction? =
    //Fallback if Uast-version of method is overridden
    createChangeModifierAction(declaration.asUast<UDeclaration>(), JavaJvmElementRenderer.render(modifier), shouldPresent)

  open fun createAddCallableMemberActions(info: MethodInsertionInfo): List<IntentionAction> = emptyList()

  open fun createAddJvmPropertyActions(psiClass: JvmClass,
                                       propertyName: String,
                                       visibilityModifier: JvmModifier,
                                       propertyType: JvmType,
                                       setterRequired: Boolean,
                                       getterRequired: Boolean): List<IntentionAction> =
    //Fallback if Uast-version of method is overridden
    createAddBeanPropertyActions(psiClass.asUast<UClass>(), propertyName,
                                 JavaJvmElementRenderer.render(visibilityModifier),
                                 JavaJvmElementMaterializer.materialize(propertyType), setterRequired, getterRequired)

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
sealed class MethodInsertionInfo(
  val targetClass: JvmClass,
  val modifiers: List<JvmModifier> = emptyList(),
  val typeParams: List<JvmTypeParameter> = emptyList(),
  val callParameters: List<JvmParameter> = emptyList()
) {

  @Deprecated("use `targetClass`", ReplaceWith("targetClass"))
  val containingClass: UClass
    get() = targetClass.asUast<UClass>()

  @Deprecated("use `targetClass`", ReplaceWith("targetClass"))
  val parameters: List<PsiParameter>
    get() = callParameters.map { JavaJvmElementMaterializer.materialize(it) }

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
             resultType = returnType,
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
    val resultType: JvmType,
    parameters: List<JvmParameter> = emptyList(),
    val isAbstract: Boolean = false
  ) : MethodInsertionInfo(targetClass, modifiers, typeParams, parameters) {
    @Deprecated("use `targetClass`", ReplaceWith("targetClass"))
    val returnType: PsiType
      get() = JavaJvmElementMaterializer.materialize(resultType)
  }

  class Constructor(
    targetClass: JvmClass,
    modifiers: List<JvmModifier> = emptyList(),
    typeParams: List<JvmTypeParameter> = emptyList(),
    parameters: List<JvmParameter> = emptyList()
  ) : MethodInsertionInfo(targetClass, modifiers, typeParams, parameters)

}

@Deprecated("remove after kotlin plugin will be ported")
private inline fun <reified T : UElement> JvmModifiersOwner.asUast(): T = when (this) {
  is T -> this
  is PsiElement -> this.let {
    ServiceManager.getService(project, UastContext::class.java)
      .convertElement(this, null, T::class.java) as T?
  }
                   ?: throw UnsupportedOperationException("cant convert $this to ${T::class}")
  else -> throw UnsupportedOperationException("cant convert $this to ${T::class}")
}

object JavaJvmElementRenderer {
  fun render(visibilityModifiers: List<JvmModifier>): String =
    visibilityModifiers.joinToString(" ") { render(it) }

  fun render(jvmType: JvmType): String =
    (jvmType as PsiType).canonicalText

  @PsiModifier.ModifierConstant
  fun render(modifier: JvmModifier): String = when (modifier) {
    JvmModifier.PUBLIC -> PsiModifier.PUBLIC
    JvmModifier.PROTECTED -> PsiModifier.PROTECTED
    JvmModifier.PRIVATE -> PsiModifier.PRIVATE
    JvmModifier.PACKAGE_LOCAL -> ""
    JvmModifier.STATIC -> PsiModifier.STATIC
    JvmModifier.ABSTRACT -> PsiModifier.ABSTRACT
    JvmModifier.FINAL -> PsiModifier.FINAL
    JvmModifier.DEFAULT -> PsiModifier.DEFAULT
    JvmModifier.NATIVE -> PsiModifier.NATIVE
    JvmModifier.SYNCHRONIZED -> PsiModifier.NATIVE
    JvmModifier.STRICTFP -> PsiModifier.STRICTFP
    JvmModifier.TRANSIENT -> PsiModifier.TRANSIENT
    JvmModifier.VOLATILE -> PsiModifier.VOLATILE
    JvmModifier.TRANSITIVE -> PsiModifier.TRANSITIVE
  }

}

object JavaJvmElementMaterializer {

  fun materialize(jvmType: JvmType): PsiType {
    return jvmType as PsiType //TODO:probably it could be not so easy sometimes
  }

  fun materialize(jvmParameter: JvmParameter): PsiParameter {
    return jvmParameter as PsiParameter //TODO:probably it could be not so easy sometimes
  }

}