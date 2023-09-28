// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.fix.CallReplacementInfo
import com.intellij.codeInspection.fix.MethodReplacementInfo
import com.intellij.codeInspection.fix.ReplaceCallableExpressionQuickFix
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

private val PROPERTIES_TO_OPTIMIZE = mapOf(
  "file.separator" to CallableExpression("java.nio.file.FileSystems",
                                         listOf(Method("getDefault",
                                                       "java.nio.file.FileSystem"),
                                                Method("getSeparator",
                                                       "java.lang.String"))),
  "path.separator" to CallableExpression("java.io.File.pathSeparator", emptyList()),
  "line.separator" to CallableExpression("java.lang.System",
                                         listOf(Method("lineSeparator",
                                                       "java.lang.String"))),
  "file.encoding" to CallableExpression("java.nio.charset.Charset",
                                        listOf(Method("defaultCharset",
                                                      "java.nio.charset.Charset"),
                                               Method("displayName",
                                                      "java.lang.String"))))

private class CallableExpression(val qualifiedReference: String, val methods: List<Method>) {
  fun toCallReplacementInfo(project: Project, scope: GlobalSearchScope): CallReplacementInfo =
    CallReplacementInfo(qualifiedReference, methods.map {
      MethodReplacementInfo(it.name, PsiType.getTypeByName(it.returnType, project, scope))
    })
}

private class Method(val name: String, val returnType: String)

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
      val message = JvmAnalysisBundle.message("jvm.inspections.system.get.property.problem.descriptor", propertyValue)
      val qualifiedReference = PROPERTIES_TO_OPTIMIZE[propertyValue] ?: error("Unknown property!")
      val scope = node.sourcePsi?.resolveScope ?: return true
      holder.registerUProblem(node, message,
                              ReplaceCallableExpressionQuickFix(qualifiedReference.toCallReplacementInfo(holder.project, scope)))
      return true
    }
  }
}