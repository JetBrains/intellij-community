// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.codeInspection.fix.ReplaceMethodCallFix
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnitAssertEqualsMayBeAssertSameInspection : AbstractBaseUastLocalInspectionTool(), CleanupLocalInspectionTool {
  private fun shouldInspect(file: PsiFile) = isJUnit3InScope(file) || isJUnit4InScope(file) || isJUnit5InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitAssertEqualsMayBeAssertSameVisitor(holder),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )
  }
}

private class JUnitAssertEqualsMayBeAssertSameVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitCallExpression(node: UCallExpression): Boolean {
    val assertHint = UAssertHint.createAssertEqualsHint(node) ?: return true
    if (!couldBeAssertSameArgument(assertHint.firstArgument)) return true
    if (!couldBeAssertSameArgument(assertHint.secondArgument)) return true
    val message = JvmAnalysisBundle.message("jvm.inspections.junit.assertequals.may.be.assertsame.problem.descriptor")
    holder.registerUProblem(node, message, ReplaceMethodCallFix("assertSame"))
    return true
  }

  private fun couldBeAssertSameArgument(expression: UExpression): Boolean {
    val argumentClass = PsiUtil.resolveClassInClassTypeOnly(expression.getExpressionType()) ?: return false
    if (!argumentClass.hasModifierProperty(PsiModifier.FINAL)) return false
    val methods = argumentClass.findMethodsByName("equals", true)
    val project = expression.sourcePsi?.project ?: return false
    val objectClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, argumentClass.resolveScope)
                      ?: return false
    for (method in methods) if (objectClass != method.containingClass) return false
    return true
  }
}