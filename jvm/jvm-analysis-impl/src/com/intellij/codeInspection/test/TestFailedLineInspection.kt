// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.PsiElementVisitor
import com.intellij.testIntegration.TestFailedLineManager
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class TestFailedLineInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, TestFailedVisitor(holder, isOnTheFly), arrayOf(UCallExpression::class.java), true)

  class TestFailedVisitor(private val holder: ProblemsHolder, private val isOnTheFly: Boolean) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      val sourceNode = node.sourcePsi ?: return true
      val testFailProvider = TestFailedLineManager.getInstance(holder.project)
      val testInfo = testFailProvider.getTestInfo(sourceNode) ?: return true
      if (testInfo.magnitude < TEST_FAILED_MAGNITUDE) return true // don't highlight skipped tests
      val fixes = listOfNotNull(
        testFailProvider.getDebugQuickFix(sourceNode, testInfo.topStackTraceLine),
        testFailProvider.getRunQuickFix(sourceNode)
      ).toTypedArray()
      val identifier = node.methodIdentifier?.sourcePsi ?: return true
      val descriptor = InspectionManager.getInstance(holder.project).createProblemDescriptor(
        identifier, testInfo.errorMessage, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      ).apply { setTextAttributes(CodeInsightColors.RUNTIME_ERROR) }
      holder.registerProblem(descriptor)
      return true
    }
  }

  companion object {
    const val TEST_FAILED_MAGNITUDE = 6 // see TestStateInfo#Magnitude
  }
}