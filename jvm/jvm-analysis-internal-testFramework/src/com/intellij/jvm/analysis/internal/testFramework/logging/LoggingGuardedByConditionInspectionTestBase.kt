package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.logging.LoggingGuardedByConditionInspection
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager.Companion.getInstance

abstract class LoggingGuardedByConditionInspectionTestBase : LoggingInspectionTestBase() {
  override val inspection: LoggingGuardedByConditionInspection = LoggingGuardedByConditionInspection()

  override fun setUp() {
    super.setUp()
    inspection.run {
      val displayKey = HighlightDisplayKey.find(this.getShortName())
      val currentProfile = getInstance(project).currentProfile
      currentProfile.setErrorLevel(displayKey!!, HighlightDisplayLevel.WARNING, project)
      inspection.showOnlyIfFixPossible = getTestName(true).endsWith("showOnlyIfFixPossible")
    }
  }
}
