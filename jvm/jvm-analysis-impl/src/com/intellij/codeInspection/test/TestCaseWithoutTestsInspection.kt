// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.options.OptPane
import com.intellij.lang.jvm.DefaultJvmElementVisitor
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmElementVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.inspection.JvmLocalInspection
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.testIntegration.JavaTestFramework
import com.siyeh.ig.junit.JUnitCommonClassNames

class TestCaseWithoutTestsInspection : JvmLocalInspection() {
  @JvmField
  var ignoreSupers = true

  override fun getOptionsPane() = OptPane.pane(OptPane.checkbox(
    "ignoreSupers",
    JvmAnalysisBundle.message("jvm.inspections.test.case.without.test.methods.option")
  ))

  override fun buildVisitor(project: Project, sink: HighlightSink, isOnTheFly: Boolean): JvmElementVisitor<Boolean> {
    return TestCaseWithoutTestsVisitor(sink, ignoreSupers)
  }
}

private class TestCaseWithoutTestsVisitor(
  private val sink: JvmLocalInspection.HighlightSink,
  private val ignoreSupers: Boolean
) : DefaultJvmElementVisitor<Boolean> {
  override fun visitClass(clazz: JvmClass): Boolean {
    if (clazz !is PsiClass) return true
    if (clazz is PsiTypeParameter) return true
    if (clazz.isEnum || clazz.isAnnotationType || clazz.isInterface || PsiUtil.isLocalOrAnonymousClass(clazz)) return true
    if (clazz.hasModifier(JvmModifier.ABSTRACT)) return true
    if (clazz.hasAnnotation(JUnitCommonClassNames.ORG_JUNIT_IGNORE)) return true
    if (clazz.hasAnnotation(JUnitCommonClassNames.ORG_JUNIT_RUNNER_RUN_WITH)) return true

    val applicableFrameworks = clazz.availableFrameworks()
    if (applicableFrameworks.isEmpty()) return true
    if (hasTestMethods(clazz, applicableFrameworks, true)) return true

    if (ignoreSupers) {
      var superClass = clazz.getSuperClass()
      while (superClass != null && clazz.getManager().isInProject(superClass)) {
        if (superClass.hasAnnotation(JUnitCommonClassNames.ORG_JUNIT_RUNNER_RUN_WITH)) return true
        val superFrameworks = superClass.availableFrameworks()
        if (hasTestMethods(superClass, superFrameworks, false)) return true
        superClass = superClass.getSuperClass()
      }
    }

    sink.highlight(JvmAnalysisBundle.message(
      "jvm.inspections.test.case.without.test.methods.problem.descriptor",
      ClassInheritorsSearch.search(clazz, clazz.getUseScope(), false).findFirst() != null
    ))
    return true
  }

  private fun PsiClass.availableFrameworks(): Collection<JavaTestFramework> {
    return TestFrameworks.detectApplicableFrameworks(this)
      .filterIsInstance<JavaTestFramework>()
      .filter { framework -> framework.isFrameworkAvailable(this) }
  }

  private fun hasTestMethods(aClass: PsiClass, frameworks: Iterable<JavaTestFramework>, checkSuite: Boolean): Boolean {
    val methods = aClass.getMethods()
    for (framework in frameworks) {
      if (checkSuite && framework.isSuiteClass(aClass)) return true
      if (methods.any { method -> framework.isTestMethod(method, false) }) return true
    }

    val nestedTestFrameworks = frameworks.filter { it.acceptNestedClasses() }
    if (nestedTestFrameworks.isEmpty()) return false
    for (innerClass in aClass.getInnerClasses()) {
      if (!innerClass.hasModifierProperty(PsiModifier.STATIC) && hasTestMethods(innerClass, nestedTestFrameworks, false)) return true
    }
    return false
  }
}