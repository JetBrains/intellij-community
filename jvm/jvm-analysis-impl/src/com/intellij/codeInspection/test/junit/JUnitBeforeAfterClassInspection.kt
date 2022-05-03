// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiType
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UastFacade

class JUnitBeforeAfterClassInspection : AbstractBaseUastLocalInspectionTool() {
  private fun isJunit4Annotation(annotation: String) = annotation.endsWith("Class")

  private fun isValidJUnit5ParamList(parameterList: List<UParameter>) = parameterList.isEmpty() // TODO check ParameterResolvers

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val sourcePsi = method.sourcePsi ?: return emptyArray()
    val javaMethod = method.javaPsi
    val annotation = ANNOTATIONS.firstOrNull {
      AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY)
    } ?: return emptyArray()
    val returnsVoid = method.returnType == PsiType.VOID
    val parameterList = method.uastParameters
    // We get alternatives because Kotlin generates 2 methods for each `JvmStatic` annotated method
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java, UMethod::class.java)).toList()
    if (isJunit4Annotation(annotation)) {
      val isStatic = alternatives.firstOrNull { it.isStatic } != null
      val isPublic = javaMethod.hasModifier(JvmModifier.PUBLIC)
      if (!isStatic || !returnsVoid || parameterList.isNotEmpty() || !isPublic) {
        return registerError(method, annotation, manager, isOnTheFly)
      }
    }
    else { // JUnit 5 annotation
      val isPrivate = javaMethod.hasModifier(JvmModifier.PRIVATE)
      val inTestInstance = alternatives.firstOrNull { it.isStatic } != null
                           || javaMethod.containingClass?.let { cls -> TestUtils.testInstancePerClass(cls) } ?: false
      if (!inTestInstance || !returnsVoid || !isValidJUnit5ParamList(parameterList) || isPrivate) {
        return registerError(method, annotation, manager, isOnTheFly)
      }
    }
    return emptyArray()
  }

  private fun registerError(
    method: UMethod,
    annotation: String,
    manager: InspectionManager,
    isOnTheFly: Boolean
  ): Array<ProblemDescriptor> {
    val message = JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation)
    val fixes = arrayOf(MakeNoArgVoidFix(method.name, true, JvmModifier.PUBLIC))
    val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
    return arrayOf(manager.createProblemDescriptor(place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
  }

  companion object {
    // JUnit 4 classes
    private const val BEFORE_CLASS = "org.junit.BeforeClass"
    private const val AFTER_CLASS = "org.junit.AfterClass"

    // JUnit 5 classes
    private const val BEFORE_ALL = "org.junit.jupiter.api.BeforeAll"
    private const val AFTER_ALL = "org.junit.jupiter.api.AfterALL"

    private val ANNOTATIONS = arrayOf(BEFORE_CLASS, AFTER_CLASS, BEFORE_ALL, AFTER_ALL)
  }
}