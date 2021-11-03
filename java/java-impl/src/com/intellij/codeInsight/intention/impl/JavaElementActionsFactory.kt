// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.actions.*
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import org.jetbrains.uast.UDeclaration
import java.util.*

class JavaElementActionsFactory : JvmElementActionsFactory() {
  override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
    val declaration = if (target is UDeclaration) target.javaPsi as PsiModifierListOwner else target as PsiModifierListOwner
    if (declaration.language != JavaLanguage.INSTANCE) return emptyList()
    return listOf(ChangeModifierFix(declaration, request))
  }
  
  internal class ChangeModifierFix(declaration: PsiModifierListOwner, @FileModifier.SafeFieldForPreview val request: ChangeModifierRequest) : 
    ModifierFix(declaration, request.modifier.toPsiModifier(), request.shouldBePresent(), true) {
    override fun isAvailable(): Boolean = request.isValid && super.isAvailable()

    override fun isAvailable(project: Project,
                             file: PsiFile,
                             editor: Editor?,
                             startElement: PsiElement,
                             endElement: PsiElement): Boolean =
      request.isValid && super.isAvailable(project, file, editor, startElement, endElement)
  } 

  override fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
    val declaration = target as? PsiModifierListOwner ?: return emptyList()
    if (declaration.language != JavaLanguage.INSTANCE) return emptyList()

    if (!AddAnnotationPsiFix.isAvailable(target, request.qualifiedName)) {
      return emptyList()
    }
    return listOf(CreateAnnotationAction(declaration, request))
  }

  override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
    val javaClass = targetClass.toJavaClassOrNull() ?: return emptyList()

    val constantRequested = request.isConstant || javaClass.isInterface || javaClass.isRecord || request.modifiers.containsAll(constantModifiers)
    val result = ArrayList<IntentionAction>()
    if (canCreateEnumConstant(javaClass, request)) {
      result += CreateEnumConstantAction(javaClass, request)
    }
    if (constantRequested || request.fieldName.uppercase(Locale.ENGLISH) == request.fieldName) {
      result += CreateConstantAction(javaClass, request)
    }
    if (!constantRequested) {
      result += CreateFieldAction(javaClass, request)
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
      // static methods in inner classes are disallowed: see JLS 8.1.3
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

  override fun createChangeParametersActions(target: JvmMethod, request: ChangeParametersRequest): List<IntentionAction> {
    val psiMethod = target as? PsiMethod ?: return emptyList()
    if (psiMethod.language != JavaLanguage.INSTANCE) return emptyList()

    if (request.expectedParameters.any { it.expectedTypes.isEmpty() || it.semanticNames.isEmpty() }) return emptyList()

    return listOf(ChangeMethodParameters(psiMethod, request))
  }

  override fun createChangeTypeActions(target: JvmMethod, request: ChangeTypeRequest): List<IntentionAction> {
    val psiMethod = target as? PsiMethod ?: return emptyList()
    if (psiMethod.language != JavaLanguage.INSTANCE) return emptyList()
    val typeElement = psiMethod.returnTypeElement ?: return emptyList()
    return listOf(ChangeType(typeElement, request))
  }

  override fun createChangeTypeActions(target: JvmParameter, request: ChangeTypeRequest): List<IntentionAction> {
    val psiParameter = target as? PsiParameter ?: return emptyList()
    if (psiParameter.language != JavaLanguage.INSTANCE) return emptyList()
    val typeElement = psiParameter.typeElement ?: return emptyList()
    return listOf(ChangeType(typeElement, request))
  }
}
