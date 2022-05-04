// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiMember
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility

class JUnitDataPointInspection : AbstractBaseUastLocalInspectionTool() {
  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(method, manager, isOnTheFly, "Method")

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(field, manager, isOnTheFly, "Field")

  private fun checkDeclaration(
    declaration: UDeclaration,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    memberDescription: @NlsSafe String
  ): Array<ProblemDescriptor> {
    val javaDecl = declaration.javaPsi.castSafelyTo<PsiMember>() ?: return emptyArray()
    val annotation = ANNOTATIONS.firstOrNull { AnnotationUtil.isAnnotated(javaDecl, it, 0) } ?: return emptyArray()
    val issues = getIssues(declaration)
    if (issues.isNotEmpty()) {
      val message = when (issues.size) {
        1 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.datapoint.problem.single.descriptor", memberDescription, annotation, issues.first()
        )
        2 -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.datapoint.problem.double.descriptor", memberDescription, annotation, issues.first(), issues.last()
        )
        else -> error("Amount of issues should be smaller than 2")
      }
      val place = declaration.uastAnchor?.sourcePsi ?: return emptyArray()
      val problemDescriptor = manager.createProblemDescriptor(
        place, message, isOnTheFly, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      )
      return arrayOf(problemDescriptor)
    }
    return emptyArray()
  }

  private fun getIssues(declaration: UDeclaration): List<@NlsSafe String> = SmartList<String>().apply {
    if (declaration.visibility != UastVisibility.PUBLIC) add("'public'")
    if (!declaration.isStatic) add("'static'")
  }

  companion object {
    private const val DATAPOINT = "org.junit.experimental.theories.DataPoint"
    private const val DATAPOINTS = "org.junit.experimental.theories.DataPoints"

    private val ANNOTATIONS = arrayOf(DATAPOINT, DATAPOINTS)
  }
}