// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.DeprecationUtil
import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MarkedForRemovalInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/forRemoval"
  }

  private val inspection = MarkedForRemovalInspection()

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(inspection)
  }

  @Test
  fun testClass(): Unit = doTest()

  @Test
  fun testField(): Unit = doTest()

  @Test
  fun testMethod(): Unit = doTest()

  @Test
  fun testOverride(): Unit = doTest()

  @Test
  fun testOverridesNonDeprecatedInterface(): Unit = doTest()

  @Test
  fun testDefaultConstructorInSuper(): Unit = doTest()
  
  @Test
  fun testWithSince(): Unit = doTest()

  @Test
  fun testSameOutermostClass(): Unit = doTest()

  @Test
  fun testAnnotationProperty(): Unit = doTest()

  @Test
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

  @Test
  fun testErrorSeverity(): Unit = doTest()

  @Test
  fun testWarningSeverity(): Unit = doSeverityTest(HighlightDisplayLevel.WARNING)

  @Test
  fun testWeakWarningSeverity(): Unit = doSeverityTest(HighlightDisplayLevel.WEAK_WARNING)

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
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}
