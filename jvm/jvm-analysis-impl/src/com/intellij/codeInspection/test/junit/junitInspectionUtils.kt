// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.junit.JUnitCommonClassNames.*

internal enum class JUnitVersion(val intRepresentation: Int) {
  V3(3), V4(4), V5(5);
}

internal val junit4Annotations = arrayOf(
  ORG_JUNIT_TEST, ORG_JUNIT_IGNORE, ORG_JUNIT_BEFORE, ORG_JUNIT_AFTER, ORG_JUNIT_BEFORE_CLASS, ORG_JUNIT_AFTER_CLASS
)

internal val junit5Annotations = arrayOf(
  ORG_JUNIT_JUPITER_API_TEST, ORG_JUNIT_JUPITER_API_DISABLED, ORG_JUNIT_JUPITER_API_BEFORE_EACH, ORG_JUNIT_JUPITER_API_AFTER_EACH,
  ORG_JUNIT_JUPITER_API_BEFORE_ALL, ORG_JUNIT_JUPITER_API_AFTER_ALL
)

/**
 * Finds the prefered test framework in case of multiple JUnit test frameworks APIs are used in a single test class.
 */
internal fun getPreferedTestFramework(clazz: PsiClass, checkSuper: Boolean = true): JUnitVersion? {
  if (checkSuper) { // check super class 1 layer deep, we assume super class only uses 1 framework
    clazz.superClass?.let { superClass ->
      val parentFramework = getPreferedTestFramework(superClass, false)
      if (parentFramework != null) return parentFramework
    }
  }
  if (InheritanceUtil.isInheritor(clazz, JUNIT_FRAMEWORK_TEST_CASE)) return JUnitVersion.V3
  val junit4Methods = clazz.methods.filter { method -> junit4Annotations.any { fqn -> method.hasAnnotation(fqn) } }
  val junit5Methods = clazz.methods.filter { method -> junit5Annotations.any { fqn -> method.hasAnnotation(fqn) } }
  if (junit4Methods.size > junit5Methods.size) return JUnitVersion.V4
  if (junit5Methods.isNotEmpty()) return JUnitVersion.V5
  return null
}

internal fun isJUnit3InScope(file: PsiFile): Boolean =
  JavaPsiFacade.getInstance(file.project).findClass(JUNIT_FRAMEWORK_TEST_CASE, file.resolveScope) != null

internal fun isJUnit4InScope(file: PsiFile): Boolean =
  JavaPsiFacade.getInstance(file.project).findClass(ORG_JUNIT_TEST, file.resolveScope) != null

internal fun isJUnit5InScope(file: PsiFile): Boolean =
  JavaPsiFacade.getInstance(file.project).findClass(ORG_JUNIT_JUPITER_API_TEST, file.resolveScope) != null