package com.intellij.jvm.analysis.internal.testFramework.performance

import com.intellij.codeInspection.performance.UrlHashCodeInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

/**
 * @deprecated Use [urlHashCodeInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use urlHashCodeInspectionFixture in a JUnit 5 test")
abstract class UrlHashCodeInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: UrlHashCodeInspection = UrlHashCodeInspection()
}
