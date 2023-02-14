// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.InheritanceUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.asSafely
import com.siyeh.ig.junit.JUnitCommonClassNames
import com.siyeh.ig.psiutils.MethodUtils
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class TestCaseWithConstructorInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      TestCaseWithConstructorVisitor(holder),
      arrayOf(UMethod::class.java), // TODO enable UClassInitializer when it has proper uastAnchor implementation
      directOnly = true
    )
}

private class TestCaseWithConstructorVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitInitializer(node: UClassInitializer): Boolean {
    if (node.isStatic) return true
    val containingClass = node.asSafely<UClassInitializerEx>()?.javaPsi?.containingClass ?: return true
    if (!TestFrameworks.getInstance().isTestClass(containingClass)) return true
    if (MethodUtils.isTrivial(node)) return true
    val message = JvmAnalysisBundle.message("jvm.inspections.test.case.with.constructor.problem.descriptor.initializer")
    holder.registerUProblem(node, message)
    return true
  }

  override fun visitMethod(node: UMethod): Boolean {
    val method = node.javaPsi
    if (!node.isConstructor) return true
    val containingClass = method.containingClass ?: return true
    if (!TestFrameworks.getInstance().isTestClass(containingClass)) return true
    if (TestUtils.isParameterizedTest(containingClass)) return true
    if (InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_SUITE)) return true
    if (MethodUtils.isTrivial(node, ::isAssignmentToFinalField)) return true
    val message = JvmAnalysisBundle.message("jvm.inspections.test.case.with.constructor.problem.descriptor")
    holder.registerUProblem(node, message)
    return true
  }

  private fun isAssignmentToFinalField(expression: UExpression): Boolean {
    val assignmentExpression = expression.asSafely<UBinaryExpression>() ?: return false
    if (assignmentExpression.operator != UastBinaryOperator.EQUALS) return false
    val lhs = assignmentExpression.leftOperand.skipParenthesizedExprDown().asSafely<UReferenceExpression>() ?: return false
    val target = lhs.resolveToUElement()
    return target is UFieldEx && target.javaPsi.hasModifier(JvmModifier.FINAL)
  }
}