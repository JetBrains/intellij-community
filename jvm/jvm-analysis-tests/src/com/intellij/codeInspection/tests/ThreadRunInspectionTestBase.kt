package com.intellij.codeInspection.tests

import com.intellij.codeInspection.ThreadRunInspection

abstract class ThreadRunInspectionTestBase : JvmInspectionTestBase() {
  override var inspection = ThreadRunInspection()
}