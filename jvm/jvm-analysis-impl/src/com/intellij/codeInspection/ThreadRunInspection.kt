// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInspection.fix.CallReplacementInfo
import com.intellij.codeInspection.fix.MethodReplacementInfo
import com.intellij.codeInspection.fix.ReplaceCallableExpressionQuickFix
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class ThreadRunInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, ThreadRunVisitor(holder), arrayOf(UCallExpression::class.java), true)

  override fun getID(): String = "CallToThreadRun"

  inner class ThreadRunVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (!THREAD_RUN.uCallMatches(node)) return true
      if (node.receiver is USuperExpression) return true
      val message = InspectionGadgetsBundle.message("thread.run.problem.descriptor")
      holder.registerUProblem(node, message, ReplaceCallableExpressionQuickFix(
        CallReplacementInfo(null,
                            listOf(MethodReplacementInfo("start")))))
      return true
    }
  }

  companion object {
    private val THREAD_RUN = CallMatcher.instanceCall("java.lang.Thread", "run").parameterCount(0)
  }
}