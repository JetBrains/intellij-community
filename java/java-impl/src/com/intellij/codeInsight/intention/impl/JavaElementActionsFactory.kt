// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.actions.*
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.light.LightRecordMember
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.ThreeState
import com.intellij.util.asSafely
import org.jetbrains.uast.UDeclaration
import java.util.*

public class JavaElementActionsFactory : JvmElementActionsFactory() {
  override fun createChangeModifierActions(target: JvmModifiersOwner, request: ChangeModifierRequest): List<IntentionAction> {
    val declaration = if (target is UDeclaration) target.javaPsi as PsiModifierListOwner else target as PsiModifierListOwner
    if (declaration.language != JavaLanguage.INSTANCE) return emptyList()
    return listOf(ChangeModifierFix(declaration, request).asIntention())
  }

  private open class RemoveAnnotationFix(private val fqn: String,
                                         element: PsiModifierListOwner,
                                         @IntentionName private val text: String,
                                         @IntentionFamilyName private val familyName: String) : 
    PsiUpdateModCommandAction<PsiModifierListOwner>(element) {

    override fun getPresentation(context: ActionContext, element: PsiModifierListOwner): Presentation? {
      return Presentation.of(text)
    }

    override fun getFamilyName(): String = familyName

    override fun invoke(context: ActionContext, element: PsiModifierListOwner, updater: ModPsiUpdater) {
      element.getAnnotation(fqn)?.delete()
      val file = element.containingFile as? PsiJavaFile ?: return
      JavaCodeStyleManager.getInstance(context.project).removeRedundantImports(file)
    }
  }

  private class RemoveOverrideAnnotationFix(element: PsiModifierListOwner) :
    RemoveAnnotationFix(
      CommonClassNames.JAVA_LANG_OVERRIDE,
      element,
      QuickFixBundle.message("remove.override.fix.text"),
      QuickFixBundle.message("remove.override.fix.family")
    )

  override fun createChangeOverrideActions(target: JvmModifiersOwner, shouldBePresent: Boolean): List<IntentionAction> {
    val psiElement = target.asSafely<PsiModifierListOwner>() ?: return emptyList()
    if (psiElement.language != JavaLanguage.INSTANCE) return emptyList()
    return if (shouldBePresent) {
      createAddAnnotationActions(target, annotationRequest(CommonClassNames.JAVA_LANG_OVERRIDE))
    }
    else {
      listOf(RemoveOverrideAnnotationFix(psiElement).asIntention())
    }
  }

  internal class ChangeModifierFix(declaration: PsiModifierListOwner,
                                   @FileModifier.SafeFieldForPreview val request: ChangeModifierRequest) :
    ModifierFix(declaration, request.modifier.toPsiModifier(), request.shouldBePresent(), true, 
                if (request.processHierarchy()) ThreeState.UNSURE else ThreeState.NO) {
    override fun getPresentation(context: ActionContext, element: PsiModifierListOwner): Presentation? {
      if (!request.isValid) return null
      return super.getPresentation(context, element)
    }
  }

  override fun createAddAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
    var declaration = target as? PsiModifierListOwner ?: return emptyList()
    if (declaration.language != JavaLanguage.INSTANCE) return emptyList()
    if (declaration is LightRecordMember) declaration = declaration.recordComponent
    if (!AddAnnotationPsiFix.isAvailable(declaration, request.qualifiedName)) {
      return emptyList()
    }
    return listOf(CreateAnnotationAction(declaration, request).asIntention())
  }

  override fun createRemoveAnnotationActions(target: JvmModifiersOwner, request: AnnotationRequest): List<IntentionAction> {
    val declaration = target as? PsiModifierListOwner ?: return emptyList()
    if (declaration.language != JavaLanguage.INSTANCE) return emptyList()
    val shortName = StringUtilRt.getShortName(request.qualifiedName)
    val text = QuickFixBundle.message("remove.annotation.fix.text", shortName)
    val familyName = QuickFixBundle.message("remove.annotation.fix.family")
    return listOf(RemoveAnnotationFix(request.qualifiedName, target, text, familyName).asIntention())
  }

  override fun createChangeAnnotationAttributeActions(annotation: JvmAnnotation,
                                                      attributeIndex: Int,
                                                      request: AnnotationAttributeRequest,
                                                      @IntentionName text: String,
                                                      @IntentionFamilyName familyName: String): List<IntentionAction> {
    val psiAnnotation = annotation as? PsiAnnotation ?: return emptyList()
    if (psiAnnotation.language != JavaLanguage.INSTANCE) return emptyList()
    return listOf(ChangeAnnotationAttributeAction(psiAnnotation, request, text, familyName).asIntention())
  }

  override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
    val javaClass = targetClass.toJavaClassOrNull() ?: return emptyList()

    val constantRequested = request.isConstant || javaClass.isInterface || javaClass.isRecord ||
                            request.modifiers.containsAll(constantModifiers)
    val result = ArrayList<IntentionAction>()
    if (canCreateEnumConstant(javaClass)) {
      val typesAgree = !request.fieldType.mapNotNull { expectedType -> (expectedType.theType as? PsiClassType)?.resolve() }.none {
        InheritanceUtil.isInheritorOrSelf(javaClass, it, true)
      }
      if (typesAgree) {
        result += CreateEnumConstantAction(javaClass, request)
      }
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
      // static methods in interfaces are allowed starting with Java 8
      if (javaClass.isInterface && !PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, javaClass)) return emptyList()
      // static methods in inner classes are disallowed before Java 16: see JLS 8.1.3
      if (javaClass.containingClass != null &&
          !javaClass.hasModifierProperty(PsiModifier.STATIC) &&
          !PsiUtil.isAvailable(JavaFeature.INNER_STATICS, javaClass)) return emptyList()
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

  override fun createChangeTypeActions(target: JvmField, request: ChangeTypeRequest): List<IntentionAction> {
    var psiField: PsiVariable = target as? PsiField ?: return emptyList()
    psiField = if (psiField is LightRecordMember) psiField.recordComponent else psiField
    if (psiField.language != JavaLanguage.INSTANCE) return emptyList()
    val typeElement = psiField.typeElement ?: return emptyList()
    return listOf(ChangeType(typeElement, request))
  }
}
