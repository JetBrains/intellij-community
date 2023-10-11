package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.JavaApiUsageInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class JavaApiUsageInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = JavaApiUsageInspection()
}