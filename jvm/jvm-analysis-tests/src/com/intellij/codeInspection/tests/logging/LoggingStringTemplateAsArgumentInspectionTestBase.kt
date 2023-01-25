package com.intellij.codeInspection.tests.logging

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.logging.LimitLevelType
import com.intellij.codeInspection.logging.LoggingStringTemplateAsArgumentInspection

abstract class LoggingStringTemplateAsArgumentInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: InspectionProfileEntry
    get() {
      val testName = getTestName(false)
      val properties = testName.split(" ").filter {
        it.contains("=")
      }.map { it.split("=") }
      val loggingStringTemplateAsArgumentInspection = LoggingStringTemplateAsArgumentInspection()
      for (property in properties) {
        if (property[0] == "myLimitLevelType") {
          loggingStringTemplateAsArgumentInspection.myLimitLevelType = LimitLevelType.values()[property[1].toInt()]
        }
        if (property[0] == "mySkipPrimitives") {
          loggingStringTemplateAsArgumentInspection.mySkipPrimitives = property[1] == "true"
        }
      }
      return loggingStringTemplateAsArgumentInspection
    }
}