// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.RefactoringUtil

class SignatureBuilder(private val project: Project) {
  private val factory: PsiElementFactory = PsiElementFactory.getInstance(project)

  fun build(
    context: PsiElement?,
    scope: List<PsiElement>,
    isStatic: Boolean = false,
    visibility: String?,
    typeParameters: List<PsiTypeParameter>,
    returnType: PsiType? = null,
    methodName: String = "extracted",
    inputParameters: List<InputParameter> = emptyList(),
    annotations: List<PsiAnnotation> = emptyList(),
    thrownExceptions: List<PsiClassType> = emptyList(),
    anchor: PsiMember
  ): PsiMethod {

    val parameterList = createParameterList(inputParameters, scope)

    val method = if (returnType != null) {
      factory.createMethod(methodName, returnType, context)
    } else {
      factory.createConstructor("methodName", context)
    }

    annotations.forEach { annotation -> method.modifierList.add(annotation) }

    JavaCodeStyleManager.getInstance(method.project).shortenClassReferences(method)

    val isInInterface = anchor.containingClass?.isInterface == true
    val isJava8 = PsiUtil.getLanguageLevel(anchor) == LanguageLevel.JDK_1_8
    val shouldHaveDefaultModifier = isJava8 && ! isStatic && isInInterface

    val typeParameterList = factory.createTypeParameterList()
    typeParameters.forEach { typeParameterList.add(it) }
    method.typeParameterList?.replace(typeParameterList)
    method.parameterList.replace(parameterList)
    method.modifierList.setModifierProperty(PsiModifier.STATIC, isStatic)
    method.modifierList.setModifierProperty(PsiModifier.DEFAULT, shouldHaveDefaultModifier)
    if (visibility != null) method.modifierList.setModifierProperty(visibility, true)
    thrownExceptions.forEach { exception -> method.throwsList.add(factory.createReferenceElementByType(exception)) }
    return method
  }

  private fun createParameterList(inputParameters: List<InputParameter>, scope: List<PsiElement>): PsiParameterList {
    val parameterList = factory.createParameterList(
      inputParameters.map { it.name }.toTypedArray(),
      inputParameters.map { it.type }.toTypedArray()
    )

    if (inputParameters.isEmpty()) return parameterList

    val element = inputParameters.first().references.first()
    val useDefaultFinal = JavaCodeStyleSettings.getInstance(scope.first().project).GENERATE_FINAL_PARAMETERS

    inputParameters.forEach { parameter ->
      val shouldBeFinal = when {
        useDefaultFinal -> parameter.references.none { reference -> PsiUtil.isAccessedForWriting(reference) }
        ! PsiUtil.isLanguageLevel8OrHigher(element) -> parameter.references.any { reference -> isInsideAnonymousOrLocal(reference, scope) }
        else -> false
      }
      val methodParameter = parameterList.parameters.find { it.name == parameter.name }
      PsiUtil.setModifierProperty(methodParameter!!, PsiModifier.FINAL, shouldBeFinal)
    }

    inputParameters.forEach { inputParameter ->
      val modifierList = parameterList.parameters.find { it.name == inputParameter.name }?.modifierList
      inputParameter.annotations.forEach { annotation -> modifierList?.add(annotation) }
    }

    return parameterList
  }

  private fun isInsideAnonymousOrLocal(element: PsiElement, scope: List<PsiElement>): Boolean {
    return scope.any { upperBound -> RefactoringUtil.isInsideAnonymousOrLocal(element, upperBound) }
  }

}