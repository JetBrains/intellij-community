package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.ThreadRunInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class ThreadRunInspectionTestBase : JvmInspectionTestBase() {
  override var inspection = ThreadRunInspection()
}