package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection

abstract class LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: LoggingPlaceholderCountMatchesArgumentCountInspection =  LoggingPlaceholderCountMatchesArgumentCountInspection().apply {
    this.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.YES
  }

  override fun tearDown() {
    super.tearDown()
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.YES
  }
}