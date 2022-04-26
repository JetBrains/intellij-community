// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.allClasses
import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.uast.UastVisitorAdapter
import com.siyeh.ig.junit.MakePublicStaticVoidFix
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class JUnitBeforeAfterClassInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    UastVisitorAdapter(Visitor(holder), true)

  private inner class Visitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitFile(node: UFile): Boolean {
      // To generate all Java PSI methods we can't use a visitor (Kotlin `JvmStatic` generates 2 Java methods for 1 Kotlin method)
      node.allClasses().forEach { cls -> cls.methods.forEach { checkJavaMethod(it) } }
      return true
    }

    private fun isJunit4Annotation(annotation: String) = annotation.endsWith("Class")

    private fun checkJavaMethod(method: UMethod) {
      val javaMethod = method.javaPsi
      val annotation = ANNOTATIONS.firstOrNull { AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY) } ?: return
      val returnType = method.returnType ?: return
      val parameterList = method.uastParameters
      if (returnType != PsiType.VOID || parameterList.isNotEmpty()) return registerError(method, annotation)
      val isStatic = javaMethod.hasModifier(JvmModifier.STATIC)
      val containingClass = javaMethod.containingClass ?: return
      // Check for test class is required here because Kotlin `JvmStatic` generates a non-static delegate
      if (!isStatic && isJunit4Annotation(annotation) && JUnitUtil.isJUnit4TestClass(containingClass)) {
        return registerError(method, annotation)
      }
      // No need to check for JUnit 5 annotation here because annotation wasn't JUnit 4
      if (!isStatic && JUnitUtil.isJUnit5TestClass(containingClass, false) && !TestUtils.testInstancePerClass(containingClass)) {
        return registerError(method, annotation)
      }
    }

    private fun registerError(method: UMethod, annotation: String) {
      val message = JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation)
      val fix = if (method.sourcePsi?.language == JavaLanguage.INSTANCE) {
        MakePublicStaticVoidFix(method.javaPsi, true)
      } else null
      val place = method.uastAnchor?.sourcePsi ?: return
      holder.registerProblem(place, message, fix)
    }
  }

  companion object {
    // JUnit 4 classes
    private const val BEFORE_CLASS = "org.junit.BeforeClass"
    private const val AFTER_CLASS = "org.junit.AfterClass"

    // JUnit 5 classes
    private const val BEFORE_ALL = "org.junit.jupiter.api.BeforeAll"
    private const val AFTER_ALL = "org.junit.jupiter.api.AfterALL"

    private val ANNOTATIONS = arrayOf(BEFORE_CLASS, AFTER_CLASS, BEFORE_ALL, AFTER_ALL)
  }
}