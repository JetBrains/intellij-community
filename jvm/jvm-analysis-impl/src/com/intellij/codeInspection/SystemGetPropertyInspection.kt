// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.quickFix.ReplaceCallableExpressionQuickFix
import com.intellij.jvm.analysis.refactoring.CallChainReplacementInfo
import com.intellij.jvm.analysis.refactoring.CallReplacementInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private val SYSTEM_GET_PROPERTY = CallMatcher.staticCall("java.lang.System", "getProperty")
  .parameterTypes("java.lang.String")

class SystemGetPropertyInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, SystemGetPropertyVisitor(holder), arrayOf(UCallExpression::class.java), true)

  private inner class SystemGetPropertyVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (!SYSTEM_GET_PROPERTY.uCallMatches(node)) return true
      val propertyValue = node.getArgumentForParameter(0)?.evaluate() as? String ?: return true
      val message = JvmAnalysisBundle.message("jvm.inspections.system.get.property.problem.descriptor", propertyValue)
      val scope = node.sourcePsi?.resolveScope ?: return true
      val qualifiedReference = buildReplacementInfo(propertyValue, holder.project, scope) ?: return true
      holder.registerUProblem(node, message,
                              ReplaceCallableExpressionQuickFix(qualifiedReference))
      return true
    }

    private fun buildReplacementInfo(keyProperty: String,
                                     project: Project,
                                     scope: GlobalSearchScope): CallChainReplacementInfo? = when (keyProperty) {
      "file.separator" -> CallChainReplacementInfo("java.nio.file.FileSystems",
                                                   CallReplacementInfo("getDefault",
                                                                       PsiType.getTypeByName("java.nio.file.FileSystem", project, scope)),
                                                   CallReplacementInfo("getSeparator",
                                                                       PsiType.getTypeByName("java.lang.String", project, scope)))
      "path.separator" -> CallChainReplacementInfo("java.io.File.pathSeparator")
      "line.separator" -> CallChainReplacementInfo("java.lang.System",
                                                   CallReplacementInfo("lineSeparator",
                                                                       PsiType.getTypeByName("java.lang.String", project, scope)))
      "file.encoding" -> CallChainReplacementInfo("java.nio.charset.Charset",
                                                  CallReplacementInfo("defaultCharset",
                                                                      PsiType.getTypeByName("java.nio.charset.Charset", project, scope)),
                                                  CallReplacementInfo("displayName",
                                                                      PsiType.getTypeByName("java.lang.String", project, scope)))
      else -> null
    }
  }
}