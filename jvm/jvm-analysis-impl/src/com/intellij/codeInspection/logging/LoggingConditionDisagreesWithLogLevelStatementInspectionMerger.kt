// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import org.jdom.Element

class LoggingConditionDisagreesWithLogLevelStatementInspectionMerger : InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "LoggingConditionDisagreesWithLogLevelStatement"

  override fun getSourceToolNames(): Array<String> = arrayOf("LoggingConditionDisagreesWithLogStatement")
  override fun isEnabledByDefault(sourceToolName: String): Boolean {
    return false
  }

  override fun writeMergedContent(toolElement: Element): Boolean {
    return true
  }
}