package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.SystemGetPropertyInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class SystemGetPropertyInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: SystemGetPropertyInspection = SystemGetPropertyInspection()
}
