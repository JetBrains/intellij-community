// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.actions.*
import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.beanProperties.CreateJavaBeanPropertyFix
import com.intellij.psi.util.PsiUtil

class JavaElementActionsFactory(private val renderer: JavaElementRenderer) : JvmElementActionsFactory() {

  override fun createChangeModifierActions(target: JvmModifiersOwner, request: MemberRequest.Modifier): List<IntentionAction> = with(
    request) {
    val declaration = target as PsiModifierListOwner
    if (declaration.language != JavaLanguage.INSTANCE) return@with emptyList()
    listOf(ModifierFix(declaration.modifierList, renderer.render(modifier), shouldPresent, false))
  }

  override fun createAddPropertyActions(targetClass: JvmClass, request: MemberRequest.Property): List<IntentionAction> {
    with(request) {
      val psiClass = targetClass.toJavaClassOrNull() ?: return emptyList()
      val helper = JvmPsiConversionHelper.getInstance(psiClass.project)
      val propertyType = helper.convertType(propertyType)
      if (getterRequired && setterRequired)
        return listOf<IntentionAction>(
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    true),
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    false))
      if (getterRequired || setterRequired)
        return listOf<IntentionAction>(
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    true),
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    false),
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, true, true, true))

      return listOf<IntentionAction>(
        CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired, true))
    }
  }

  override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
    val javaClass = targetClass.toJavaClassOrNull() ?: return emptyList()

    val constantRequested = request.constant || javaClass.isInterface || request.modifiers.containsAll(constantModifiers)
    val result = ArrayList<IntentionAction>()
    if (constantRequested || StringUtil.isCapitalized(request.fieldName)) {
      result += CreateConstantAction(javaClass, request)
    }
    if (!constantRequested) {
      result += CreateFieldAction(javaClass, request)
    }
    if (canCreateEnumConstant(javaClass, request)) {
      result += CreateEnumConstantAction(javaClass, request)
    }
    return result
  }

  override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
    val javaClass = targetClass.toJavaClassOrNull() ?: return emptyList()

    val requestedModifiers = request.modifiers
    val staticMethodRequested = JvmModifier.STATIC in requestedModifiers

    if (staticMethodRequested) {
      // static method in interfaces are allowed starting with Java 8
      if (javaClass.isInterface && !PsiUtil.isLanguageLevel8OrHigher(javaClass)) return emptyList()
      // static methods in inner classes are disallowed JLS §8.1.3
      if (javaClass.containingClass != null && !javaClass.hasModifierProperty(PsiModifier.STATIC)) return emptyList()
    }

    val result = ArrayList<IntentionAction>()
    result += CreateMethodAction(javaClass, request, false)
    if (!staticMethodRequested && javaClass.hasModifierProperty(PsiModifier.ABSTRACT) && !javaClass.isInterface) {
      result += CreateMethodAction(javaClass, request, true)
    }
    if (!javaClass.isInterface) {
      result += CreatePropertyAction(javaClass, request)
      result += CreateGetterWithFieldAction(javaClass, request)
      result += CreateSetterWithFieldAction(javaClass, request)
    }
    return result
  }

  override fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
    val javaClass = targetClass.toJavaClassOrNull() ?: return emptyList()
    return listOf(CreateConstructorAction(javaClass, request))
  }
}

class JavaElementRenderer {
  companion object {
    @JvmStatic
    fun getInstance(): JavaElementRenderer {
      return ServiceManager.getService(JavaElementRenderer::class.java)
    }
  }


  fun render(visibilityModifiers: List<JvmModifier>): String =
    visibilityModifiers.joinToString(" ") { render(it) }

  fun render(jvmType: JvmType): String =
    (jvmType as PsiType).canonicalText

  fun render(jvmAnnotation: JvmAnnotation): String =
    "@" + (jvmAnnotation as PsiAnnotation).qualifiedName!!

  @PsiModifier.ModifierConstant
  fun render(modifier: JvmModifier): String = modifier.toPsiModifier()

}
