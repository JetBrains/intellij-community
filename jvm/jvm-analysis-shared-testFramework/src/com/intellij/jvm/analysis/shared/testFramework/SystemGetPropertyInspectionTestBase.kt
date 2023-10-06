package com.intellij.jvm.analysis.shared.testFramework

import com.intellij.codeInspection.SystemGetPropertyInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class SystemGetPropertyInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = SystemGetPropertyInspection()
}