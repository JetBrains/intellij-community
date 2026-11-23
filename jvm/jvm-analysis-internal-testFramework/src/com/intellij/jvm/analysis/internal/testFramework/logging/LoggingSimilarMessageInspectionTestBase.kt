package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.codeInspection.logging.LoggingSimilarMessageInspection

/**
 * @deprecated Use [loggingSimilarMessageInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use loggingSimilarMessageInspectionFixture in a JUnit 5 test")
abstract class LoggingSimilarMessageInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: LoggingSimilarMessageInspection = LoggingSimilarMessageInspection()
}
