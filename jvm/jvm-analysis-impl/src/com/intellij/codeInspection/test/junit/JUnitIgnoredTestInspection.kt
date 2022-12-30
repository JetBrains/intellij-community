// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_IGNORE
import com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_DISABLED
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import javax.swing.JComponent

class JUnitIgnoredTestInspection : AbstractBaseUastLocalInspectionTool() {
  @JvmField
  var onlyReportWithoutReason = true

  override fun createOptionsPanel(): JComponent = SingleCheckboxOptionsPanel(
    JvmAnalysisBundle.message("jvm.inspections.junit.ignored.test.ignore.reason.option"), this, "onlyReportWithoutReason"
  )

  private fun shouldInspect(file: PsiFile) = isJUnit4InScope(file) || isJUnit5InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitIgnoredTestVisitor(holder, onlyReportWithoutReason),
      arrayOf(UClass::class.java, UMethod::class.java),
      directOnly = true
    )
  }
}

private class JUnitIgnoredTestVisitor(
  private val holder: ProblemsHolder,
  private val onlyReportWithoutReason: Boolean
) : AbstractUastNonRecursiveVisitor() {
  val withoutReasonChoice = if (onlyReportWithoutReason) 2 else 1

  override fun visitClass(node: UClass): Boolean = checkIgnoreOrDisabled(
    node, JvmAnalysisBundle.message("jvm.inspections.junit.ignored.test.class.problem.descriptor", node.javaPsi.name, withoutReasonChoice)
  )

  override fun visitMethod(node: UMethod): Boolean = checkIgnoreOrDisabled(
    node, JvmAnalysisBundle.message("jvm.inspections.junit.ignored.test.method.problem.descriptor", node.name, withoutReasonChoice)
  )

  private fun checkIgnoreOrDisabled(node: UDeclaration, message: @InspectionMessage String): Boolean {
    val annotations = node.findAnnotations(ORG_JUNIT_IGNORE, ORG_JUNIT_JUPITER_API_DISABLED)
    if (annotations.isEmpty()) return true
    if (onlyReportWithoutReason && annotations.any { it.findDeclaredAttributeValue("value") != null }) return true
    holder.registerUProblem(node, message)
    return true
  }
}