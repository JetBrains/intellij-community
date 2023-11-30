// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastVisitorAdapter
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnit4RunWithInspection : AbstractBaseUastLocalInspectionTool() {
  private fun shouldInspect(file: PsiFile) = isJUnit4InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastVisitorAdapter(RunWithVisitor(holder), true)
  }
}

private class RunWithVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitAnnotation(node: UAnnotation): Boolean {
    if (node.qualifiedName != TestUtils.RUN_WITH) return super.visitAnnotation(node)
    val current = node.uastParent
    if (current == null || current !is UClass) return super.visitAnnotation(node)

    var parent = current.javaPsi.superClass
    while (parent != null) {
      if (parent.hasAnnotation(TestUtils.RUN_WITH)) {
        val message = JvmAnalysisBundle.message("jvm.inspections.junit4.inherited.runwith.problem.descriptor", parent.name)
        holder.registerUProblem(node, message, highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        return true
      }
      parent = parent.superClass
    }

    return super.visitAnnotation(node)
  }
}