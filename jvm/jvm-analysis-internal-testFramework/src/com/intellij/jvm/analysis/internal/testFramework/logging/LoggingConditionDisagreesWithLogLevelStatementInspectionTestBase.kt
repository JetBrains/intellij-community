package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.codeInspection.logging.LoggingConditionDisagreesWithLogLevelStatementInspection

/**
 * @deprecated Use [loggingConditionDisagreesWithLogLevelStatementInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use loggingConditionDisagreesWithLogLevelStatementInspectionFixture in a JUnit 5 test")
abstract class LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: LoggingConditionDisagreesWithLogLevelStatementInspection = LoggingConditionDisagreesWithLogLevelStatementInspection()
}
