// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21

class DuplicateBranchesInSwitchFixTest : LightQuickFixParameterizedTestCase() {

  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> = arrayOf(DuplicateBranchesInSwitchInspection())

  override fun getBasePath() = "/inspection/duplicateBranchesInSwitchFix"

  override fun getProjectDescriptor() = JAVA_21
}