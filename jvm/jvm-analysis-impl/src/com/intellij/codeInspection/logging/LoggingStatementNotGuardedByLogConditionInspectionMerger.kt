// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element

class LoggingStatementNotGuardedByLogConditionInspectionMerger : InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "LogStatementNotGuardedByLogCondition"

  override fun getSourceToolNames(): Array<String> = arrayOf("LogStatementGuardedByLogCondition")

  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    val inspection = LoggingStatementNotGuardedByLogConditionInspection()
    inspection.flagUnguardedConstant = JDOMExternalizerUtil.readField(sourceElement, "flagAllUnguarded", "false").toBoolean()
    inspection.writeSettings(toolElement)
    return toolElement
  }
}