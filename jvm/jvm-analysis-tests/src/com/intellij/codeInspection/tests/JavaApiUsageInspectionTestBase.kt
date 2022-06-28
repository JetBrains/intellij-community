package com.intellij.codeInspection.tests

import com.intellij.codeInspection.JavaApiUsageInspection

abstract class JavaApiUsageInspectionTestBase : UastInspectionTestBase() {
  override val inspection = JavaApiUsageInspection()
}