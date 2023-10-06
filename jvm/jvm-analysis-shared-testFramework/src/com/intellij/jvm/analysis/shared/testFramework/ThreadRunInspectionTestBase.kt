package com.intellij.jvm.analysis.shared.testFramework

import com.intellij.codeInspection.ThreadRunInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class ThreadRunInspectionTestBase : JvmInspectionTestBase() {
  override var inspection = ThreadRunInspection()
}