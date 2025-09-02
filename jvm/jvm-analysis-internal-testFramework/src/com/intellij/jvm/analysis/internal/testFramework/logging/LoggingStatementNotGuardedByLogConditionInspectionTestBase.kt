package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.logging.LoggingStatementNotGuardedByLogConditionInspection
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager.Companion.getInstance
import com.intellij.codeInspection.logging.LoggingUtil

abstract class LoggingStatementNotGuardedByLogConditionInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: LoggingStatementNotGuardedByLogConditionInspection = LoggingStatementNotGuardedByLogConditionInspection()

  override fun setUp() {
    super.setUp()
    inspection.run {
      myLimitLevelType = LoggingUtil.LimitLevelType.INFO_AND_LOWER
      val displayKey = HighlightDisplayKey.find(this.getShortName())
      val currentProfile = getInstance(project).currentProfile
      currentProfile.setErrorLevel(displayKey!!, HighlightDisplayLevel.WARNING, project)
      inspection.flagUnguardedConstant = getTestName(true).endsWith("flagUnguardedConstant")
    }
  }
}
