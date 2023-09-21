// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInspection.fix.ReplaceReferenceQuickFix
import com.intellij.codeInspection.fix.ReplaceReferenceQuickFix.Companion.PROPERTIES_TO_OPTIMIZE
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private val SYSTEM_GET_PROPERTY = CallMatcher.staticCall("java.lang.System", "getProperty")
  .parameterCount(1)

class SystemGetPropertyInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, SystemGetPropertyVisitor(holder), arrayOf(UCallExpression::class.java), true)

  private inner class SystemGetPropertyVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (!SYSTEM_GET_PROPERTY.uCallMatches(node)) return true
      val propertyValue = node.getArgumentForParameter(0)?.evaluate() as? String ?: return true
      if (propertyValue !in PROPERTIES_TO_OPTIMIZE.keys) return true
      val message = InspectionGadgetsBundle.message("system.get.property.problem.descriptor", propertyValue)
      holder.registerUProblem(node, message, ReplaceReferenceQuickFix(propertyValue))
      return true
    }
  }

}