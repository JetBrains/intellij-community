// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.util.castSafelyTo
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastVisibility
import javax.swing.JComponent

class JUnitMalformedTestInspection : AbstractBaseUastLocalInspectionTool(UMethod::class.java) {
  val ignorableAnnotations: List<String> = ArrayList(listOf("mockit.Mocked"))

  override fun createOptionsPanel(): JComponent = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
    ignorableAnnotations, InspectionGadgetsBundle.message("ignore.parameter.if.annotated.by")
  )

  private enum class Problem(val message: @PropertyKey(resourceBundle = JvmAnalysisBundle.BUNDLE) String) {
    STATIC("jvm.inspections.test.method.is.public.void.no.arg.problem.static"),
    NOT_PUBLIC_VOID("jvm.inspections.test.method.is.public.void.no.arg.problem.public.void"),
    PARAMETER("jvm.inspections.test.method.is.public.void.no.arg.problem.no.param")
  }

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val sourcePsi = method.sourcePsi ?: return emptyArray()
    val alternatives = UastFacade.convertToAlternatives(sourcePsi, arrayOf(UMethod::class.java, UMethod::class.java))
    val javaMethod = alternatives.firstOrNull { it.isStatic } ?: alternatives.first() // gets synthetic static method in case of Kotlin
    if (method.isConstructor) return emptyArray()
    if (!TestUtils.isJUnit3TestMethod(javaMethod.javaPsi) && !TestUtils.isJUnit4TestMethod(javaMethod.javaPsi)) return emptyArray()
    val containingClass = method.javaPsi.containingClass ?: return emptyArray()
    if (AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, AnnotationUtil.CHECK_HIERARCHY)) return emptyArray()
    if (PsiType.VOID != method.returnType || method.visibility != UastVisibility.PUBLIC) {
      return registerError(method, Problem.NOT_PUBLIC_VOID, manager, isOnTheFly)
    }
    val parameterList = method.uastParameters
    if (parameterList.isNotEmpty() && parameterList.any { param ->
        param.javaPsi?.castSafelyTo<PsiParameter>()?.let { !AnnotationUtil.isAnnotated(it, ignorableAnnotations, 0) } == true
      }) {
      return registerError(method, Problem.PARAMETER, manager, isOnTheFly)
    }
    if (javaMethod.isStatic) {
      return registerError(method, Problem.STATIC, manager, isOnTheFly)
    }
    return emptyArray()
  }

  private fun registerError(
    method: UMethod,
    problem: Problem,
    manager: InspectionManager,
    isOnTheFly: Boolean
  ): Array<ProblemDescriptor> {
    val message = JvmAnalysisBundle.message(problem.message)
    val fixes = arrayOf(MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC))
    val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
    return arrayOf(manager.createProblemDescriptor(place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
  }
}