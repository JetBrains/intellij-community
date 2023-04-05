package com.intellij.codeInspection.tests.logging

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection

abstract class LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: InspectionProfileEntry
    get() {
      val argumentCountInspection = LoggingPlaceholderCountMatchesArgumentCountInspection()
      argumentCountInspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.YES
      return argumentCountInspection
    }
}