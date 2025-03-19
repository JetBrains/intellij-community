// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.DeprecationUtil
import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class MarkedForRemovalInspectionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getTestDataPath() = JavaTestUtil.getJavaTestDataPath() + "/inspection/forRemoval"

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  private val inspection = MarkedForRemovalInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  fun testClass() = doTest()

  fun testField() = doTest()

  fun testMethod() = doTest()

  fun testOverride() = doTest()

  fun testDefaultConstructorInSuper() = doTest()
  
  fun testWithSince() = doTest()

  fun testSameOutermostClass() = doTest()

  fun testAnnotationProperty() = doTest()

  fun testSameOutermostClassOff() {
    val oldIgnore = inspection.IGNORE_IN_SAME_OUTERMOST_CLASS
    try {
      inspection.IGNORE_IN_SAME_OUTERMOST_CLASS = false
      doTest()
    }
    finally {
      inspection.IGNORE_IN_SAME_OUTERMOST_CLASS = oldIgnore
    }
  }

  fun testErrorSeverity() = doTest()

  fun testWarningSeverity() = doSeverityTest(HighlightDisplayLevel.WARNING)

  fun testWeakWarningSeverity() = doSeverityTest(HighlightDisplayLevel.WEAK_WARNING)

  private fun doSeverityTest(severityLevel: HighlightDisplayLevel) {
    val forRemovalKey: HighlightDisplayKey = HighlightDisplayKey.find(DeprecationUtil.FOR_REMOVAL_SHORT_NAME)!!
    val profile = InspectionProjectProfileManager.getInstance(myFixture.project).currentProfile
    val oldLevel = profile.getErrorLevel(forRemovalKey, myFixture.file)
    profile.setErrorLevel(forRemovalKey, severityLevel, myFixture.project)

    try {
      doTest()
    }
    finally {
      profile.setErrorLevel(forRemovalKey, oldLevel, myFixture.project)
    }
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}
