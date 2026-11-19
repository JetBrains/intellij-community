package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.JavaApiUsageInspection

/**
 * @deprecated Use [jvmSdkProjectFixture] and [javaApiUsageInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use jvmSdkProjectFixture and javaApiUsageInspectionFixture in a JUnit 5 test")
abstract class JavaApiUsageInspectionTestBase : JvmSdkInspectionTestBase() {
  override val inspection: JavaApiUsageInspection = JavaApiUsageInspection()
}
