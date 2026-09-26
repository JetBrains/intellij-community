// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class DuplicateBranchesInEnhancedSwitchFix21PreviewTest : LightQuickFixParameterizedTestCase() {

  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> = arrayOf(DuplicateBranchesInSwitchInspection())

  override fun getBasePath() = "/inspection/duplicateBranchesInEnhancedSwitchFix21Preview"

  override fun getLanguageLevel(): LanguageLevel {
    return LanguageLevel.JDK_21_PREVIEW
  }

  override fun getProjectDescriptor() = LightJavaCodeInsightFixtureTestCase.JAVA_21
}