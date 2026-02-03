package com.intellij.jvm.analysis.internal.testFramework.performance

import com.intellij.codeInspection.performance.UrlHashCodeInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class UrlHashCodeInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: UrlHashCodeInspection = UrlHashCodeInspection()
}
