package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.JavaApiUsageInspection

abstract class JavaApiUsageInspectionTestBase : JvmSdkInspectionTestBase() {
  override val inspection: JavaApiUsageInspection = JavaApiUsageInspection()
}
