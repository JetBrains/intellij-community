package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.SystemGetPropertyInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

/**
 * @deprecated Use [systemGetPropertyInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use systemGetPropertyInspectionFixture in a JUnit 5 test")
abstract class SystemGetPropertyInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: SystemGetPropertyInspection = SystemGetPropertyInspection()
}
