// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.text.VersionComparatorUtil
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import org.jetbrains.uast.UElement

internal fun isJUnit3InScope(file: PsiFile): Boolean {
  return JavaLibraryUtil.hasLibraryClass(ModuleUtil.findModuleForFile(file), JUNIT_FRAMEWORK_TEST_CASE)
}

internal fun isJUnit4InScope(file: PsiFile): Boolean {
  return JavaLibraryUtil.hasLibraryClass(ModuleUtil.findModuleForFile(file), ORG_JUNIT_TEST)
}

internal fun isJUnit5InScope(file: PsiFile): Boolean {
  return JavaLibraryUtil.hasLibraryClass(ModuleUtil.findModuleForFile(file), ORG_JUNIT_JUPITER_API_TEST)
}

class JUnitVersion(val asString: String) : Comparable<JUnitVersion> {
  override fun compareTo(other: JUnitVersion): Int {
    return VersionComparatorUtil.compare(asString, other.asString)
  }

  companion object {
    val V_3_X = JUnitVersion("3")
    val V_4_X = JUnitVersion("4")
    val V_5_X = JUnitVersion("5")
    val V_5_8_0 = JUnitVersion("5.8.0")
    val V_5_10_0 = JUnitVersion("5.10.0")
  }
}

private const val JUNIT_3_AND_4_COORDINATES = "junit:junit"

private const val JUNIT_5_COORDINATES = "org.junit.jupiter:junit-jupiter-api"

internal fun getUJUnitVersion(elem: UElement): JUnitVersion? {
  val sourcePsi = elem.sourcePsi ?: return null
  return getJUnitVersion(sourcePsi)
}

internal fun getJUnitVersion(elem: PsiElement): JUnitVersion? {
  val module = ModuleUtil.findModuleForPsiElement(elem) ?: return null
  return getJUnitVersion(module)
}

/**
 * Gets latest available JUnit version in the class path.
 */
internal fun getJUnitVersion(module: Module): JUnitVersion? {
  if (module.isDisposed() || module.getProject().isDefault) return null
  val junit5Version = JavaLibraryUtil.getLibraryVersion(module, JUNIT_5_COORDINATES)?.substringBeforeLast("-")
  if (junit5Version != null) return JUnitVersion(junit5Version)
  val junit3Or4Version = JavaLibraryUtil.getLibraryVersion(module, JUNIT_3_AND_4_COORDINATES)?.substringBeforeLast("-")
  if (junit3Or4Version != null) return JUnitVersion(junit3Or4Version)
  return null
}