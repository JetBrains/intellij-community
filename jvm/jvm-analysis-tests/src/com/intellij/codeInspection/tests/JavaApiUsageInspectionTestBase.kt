package com.intellij.codeInspection.tests

import com.intellij.codeInspection.JavaApiUsageInspection

abstract class JavaApiUsageInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = JavaApiUsageInspection()
}