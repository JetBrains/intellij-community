package com.intellij.codeInspection.tests.logging

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.logging.LoggingStringTemplateAsArgumentInspection

abstract class LoggingStringTemplateAsArgumentInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: InspectionProfileEntry
    get() = LoggingStringTemplateAsArgumentInspection()
}