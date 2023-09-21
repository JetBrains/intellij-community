package com.intellij.codeInspection.tests

import com.intellij.codeInspection.SystemGetPropertyInspection

abstract class SystemGetPropertyInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = SystemGetPropertyInspection()
}