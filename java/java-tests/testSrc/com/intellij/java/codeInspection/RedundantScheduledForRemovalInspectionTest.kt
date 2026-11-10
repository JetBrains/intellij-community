// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.deprecation.RedundantScheduledForRemovalAnnotationInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspection/redundantScheduledForRemoval")
@TestApplication
class RedundantScheduledForRemovalInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/inspection/redundantScheduledForRemoval"
  }

  @BeforeEach
  fun setUp() {
    IdeaTestUtil.setProjectLanguageLevel(myFixture.project, LanguageLevel.JDK_1_9)
    myFixture.enableInspections(RedundantScheduledForRemovalAnnotationInspection())
    myFixture.addClass("""
      |package org.jetbrains.annotations; 
      |public class ApiStatus {  
      |  public @interface ScheduledForRemoval {
      |    String inVersion() default "";
      |  }
      |}""".trimMargin())
  }

  @Test
  fun `test replace by attribute`() {
    myFixture.testHighlighting("ReplaceScheduledForRemovalByAttribute.java")
    myFixture.launchAction(myFixture.findSingleIntention("Replace @ScheduledForRemoval"))
    myFixture.checkResultByFile("ReplaceScheduledForRemovalByAttribute_after.java")
  }

  @Test
  fun `test remove`() {
    myFixture.testHighlighting("RemoveScheduledForRemoval.java")
    myFixture.launchAction(myFixture.findSingleIntention("Remove annotation"))
    myFixture.checkResultByFile("RemoveScheduledForRemoval_after.java")
  }

  @Test
  fun `test do not higlight if version is specified`() {
    myFixture.testHighlighting("ScheduledForRemovalWithVersion.java")
  }
}