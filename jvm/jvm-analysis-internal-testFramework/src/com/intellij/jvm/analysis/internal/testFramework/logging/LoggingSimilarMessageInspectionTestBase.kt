package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.codeInspection.logging.LoggingSimilarMessageInspection

abstract class LoggingSimilarMessageInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: LoggingSimilarMessageInspection = LoggingSimilarMessageInspection()
}
