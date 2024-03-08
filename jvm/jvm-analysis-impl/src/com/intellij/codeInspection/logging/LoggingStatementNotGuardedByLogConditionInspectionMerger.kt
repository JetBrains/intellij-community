// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.siyeh.ig.BaseInspection
import com.siyeh.ig.psiutils.JavaLoggingUtils
import org.jdom.Element

class LoggingStatementNotGuardedByLogConditionInspectionMerger : InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "LogStatementNotGuardedByLogCondition"

  override fun getSourceToolNames(): Array<String> = arrayOf("LogStatementGuardedByLogCondition")


  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    val inspection = LoggingStatementNotGuardedByLogConditionInspection()
    inspection.customLoggerClassName = JDOMExternalizerUtil.readField(sourceElement, "loggerClassName") ?: JavaLoggingUtils.JAVA_LOGGING
    inspection.flagUnguardedConstant = JDOMExternalizerUtil.readField(sourceElement, "flagAllUnguarded", "false").toBoolean()
    val conditionMethodNames = JDOMExternalizerUtil.readField(sourceElement, "loggerMethodAndconditionMethodNames")
    if (conditionMethodNames != null) {
      val calls = mutableListOf<String?>()
      val condition = mutableListOf<String?>()
      BaseInspection.parseString(conditionMethodNames, calls, condition)
      inspection.customLogMethodNameList = calls
      inspection.customLogConditionMethodNameList = condition
    }
    inspection.writeSettings(toolElement)
    return toolElement
  }
}