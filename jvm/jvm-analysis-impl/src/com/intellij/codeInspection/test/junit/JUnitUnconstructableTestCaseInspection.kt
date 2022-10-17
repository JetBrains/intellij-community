// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTypeParameter
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.psiutils.TestUtils
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.UClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnitUnconstructableTestCaseInspection : AbstractBaseUastLocalInspectionTool() {
  private fun shouldInspect(file: PsiFile) = isJUnit3InScope(file) || isJUnit4InScope(file)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      JUnitUnconstructableTestCaseVisitor(holder),
      arrayOf(UClass::class.java),
      directOnly = true
    )
  }
}

private class JUnitUnconstructableTestCaseVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitClass(node: UClass): Boolean {
    val javaNode = node.javaPsi
    if (javaNode.isInterface || javaNode.isEnum || javaNode.isAnnotationType) return true
    if (javaNode.hasModifier(JvmModifier.ABSTRACT)) return true
    if (javaNode is PsiTypeParameter) return true
    if (TestUtils.isJUnitTestClass(javaNode)) { // JUnit 3
      if (!javaNode.hasModifier(JvmModifier.PUBLIC) && !node.isAnonymousOrLocal()) {
        val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.not.public.descriptor")
        holder.registerUProblem(node, message)
        return true
      }
      val constructors = javaNode.constructors.toList()
      if (constructors.isNotEmpty()) {
        val compatibleConstr = constructors.firstOrNull {
          val parameters = it.parameterList.parameters
          it.hasModifier(JvmModifier.PUBLIC)
          && (it.parameterList.isEmpty || parameters.size == 1 && TypeUtils.isJavaLangString(parameters.first().type))
        }
        if (compatibleConstr == null) {
          val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.junit3.descriptor")
          holder.registerUProblem(node, message)
          return true
        }
      }
    } else if (TestUtils.isJUnit4TestClass(javaNode, false)) { // JUnit 4
      if (!javaNode.hasModifier(JvmModifier.PUBLIC) && !node.isAnonymousOrLocal()) {
        val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.not.public.descriptor")
        holder.registerUProblem(node, message)
        return true
      }
      val constructors = javaNode.constructors.toList()
      if (constructors.isNotEmpty()) {
        val publicConstructors = constructors.filter { it.hasModifier(JvmModifier.PUBLIC) }
        if (publicConstructors.size != 1 || !publicConstructors.first().parameterList.isEmpty) {
          val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.junit4.descriptor")
          holder.registerUProblem(node, message)
          return true
        }
      }
    }
    return true
  }
}