// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.RefactoringUtil

class SignatureBuilder(private val project: Project) {
  private val factory: PsiElementFactory = PsiElementFactory.getInstance(project)

  fun build(
    targetClass: PsiClass,
    scope: List<PsiElement>,
    isStatic: Boolean = false,
    visibility: String?,
    typeParameters: List<PsiTypeParameter>,
    returnType: PsiType? = null,
    methodName: String = "extracted",
    inputParameters: List<InputParameter> = emptyList(),
    annotations: List<PsiAnnotation> = emptyList(),
    thrownExceptions: List<PsiClassType> = emptyList()
  ): PsiMethod {

    val parameterList = createParameterList(inputParameters, scope)

    val method = if (returnType != null) {
      factory.createMethod(methodName, PsiTypesUtil.removeExternalAnnotations(returnType), targetClass)
    } else {
      factory.createConstructor("methodName", targetClass)
    }

    copyNotPresentAnnotations(annotations, method)

    val typeParameterList = factory.createTypeParameterList()
    typeParameters.forEach { typeParameterList.add(it) }
    method.typeParameterList?.replace(typeParameterList)
    method.parameterList.replace(parameterList)

    method.modifierList.setModifierProperty(PsiModifier.STATIC, isStatic)
    if (visibility != null && visibilityCanBeApplied(targetClass, visibility)) {
      method.modifierList.setModifierProperty(visibility, true)
    }
    if (targetClass.isInterface && !method.hasModifierProperty(PsiModifier.STATIC) && method.hasModifierProperty(PsiModifier.PUBLIC)) {
      method.modifierList.setModifierProperty(PsiModifier.DEFAULT, true)
    }
    thrownExceptions.forEach { exception -> method.throwsList.add(factory.createReferenceElementByType(exception)) }
    return JavaCodeStyleManager.getInstance(method.project).shortenClassReferences(method) as PsiMethod
  }

  private fun visibilityCanBeApplied(targetClass: PsiClass, visibility: String): Boolean {
    return !targetClass.isInterface
           || (visibility == PsiModifier.PRIVATE && PsiUtil.isAvailable(JavaFeature.PRIVATE_INTERFACE_METHODS, targetClass))
  }

  private fun createParameterList(inputParameters: List<InputParameter>, scope: List<PsiElement>): PsiParameterList {
    val parameterList = factory.createParameterList(
      inputParameters.map { it.name }.toTypedArray(),
      inputParameters.map { it.type }.toTypedArray()
    )

    if (inputParameters.isEmpty()) return parameterList

    val element = inputParameters.first().references.first()
    val useDefaultFinal = JavaCodeStyleSettings.getInstance(scope.first().containingFile).GENERATE_FINAL_PARAMETERS

    inputParameters.forEach { parameter ->
      val shouldBeFinal = when {
        useDefaultFinal -> parameter.references.none { reference -> PsiUtil.isAccessedForWriting(reference) }
        !PsiUtil.isAvailable(JavaFeature.EFFECTIVELY_FINAL, element) -> parameter.references.any { reference -> isInsideAnonymousOrLocal(reference, scope) }
        else -> false
      }
      val methodParameter = parameterList.parameters.find { it.name == parameter.name }
      PsiUtil.setModifierProperty(methodParameter!!, PsiModifier.FINAL, shouldBeFinal)
    }

    inputParameters.forEach { inputParameter ->
      val parameter = parameterList.parameters.find { it.name == inputParameter.name }
      if (parameter != null) {
        copyNotPresentAnnotations(inputParameter.annotations, parameter)
      }
    }

    return parameterList
  }

  private fun copyNotPresentAnnotations(annotations: List<PsiAnnotation>,
                                        modifierListOwner: PsiModifierListOwner) {
    val modifierList = modifierListOwner.modifierList
    annotations.forEach { annotation ->
      val qualifiedName = annotation.qualifiedName
      if (qualifiedName != null && !AnnotationUtil.isAnnotated(modifierListOwner, qualifiedName, AnnotationUtil.CHECK_TYPE)) {
        modifierList?.add(annotation)
      }
    }
  }

  private fun isInsideAnonymousOrLocal(element: PsiElement, scope: List<PsiElement>): Boolean {
    return scope.any { upperBound -> RefactoringUtil.isInsideAnonymousOrLocal(element, upperBound) }
  }

}