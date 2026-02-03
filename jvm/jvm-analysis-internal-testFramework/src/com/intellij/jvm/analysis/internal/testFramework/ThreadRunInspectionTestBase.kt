package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.ThreadRunInspection

abstract class ThreadRunInspectionTestBase : JvmSdkInspectionTestBase() {
  override var inspection: ThreadRunInspection = ThreadRunInspection()
}
