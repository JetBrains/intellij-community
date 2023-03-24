package com.intellij.codeInspection.tests.logging

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.logging.LoggingConditionDisagreesWithLogLevelStatementInspection

abstract class LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase : LoggingInspectionTestBase(){
  override val inspection: InspectionProfileEntry
    get() = LoggingConditionDisagreesWithLogLevelStatementInspection()
}