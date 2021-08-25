package com.intellij.codeInspection.tests

import com.intellij.codeInspection.ThreadRunInspection

abstract class ThreadRunInspectionTestBase : UastInspectionTestBase() {
  override var inspection = ThreadRunInspection()
}