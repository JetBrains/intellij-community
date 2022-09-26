// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class TestInProductSourceInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      TestInProductSourceVisitor(holder),
      arrayOf(UClass::class.java, UMethod::class.java),
      directOnly = true
    )
}

private class TestInProductSourceVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitClass(node: UClass): Boolean {
    val javaClass = node.javaPsi
    if (TestUtils.isInTestSourceContent(javaClass) || !TestFrameworks.getInstance().isTestClass(javaClass)) return true
    val message = JvmAnalysisBundle.message("jvm.inspections.test.case.in.product.source.problem.descriptor")
    holder.registerUProblem(node, message)
    return true
  }

  override fun visitMethod(node: UMethod): Boolean {
    val method = node.javaPsi
    val containingClass = method.containingClass ?: return true
    if (TestUtils.isInTestSourceContent(containingClass) || !TestFrameworks.getInstance().isTestMethod(method)) return true
    val message = JvmAnalysisBundle.message("jvm.inspections.test.method.in.product.source.problem.descriptor")
    holder.registerUProblem(node, message)
    return true
  }
}