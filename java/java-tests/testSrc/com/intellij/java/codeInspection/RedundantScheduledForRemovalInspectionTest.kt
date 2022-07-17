// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.deprecation.RedundantScheduledForRemovalAnnotationInspection
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

@TestDataPath("\$CONTENT_ROOT/testData/inspection/redundantScheduledForRemoval")
class RedundantScheduledForRemovalInspectionTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String = "${JavaTestUtil.getJavaTestDataPath()}/inspection/redundantScheduledForRemoval"

  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_9
    myFixture.enableInspections(RedundantScheduledForRemovalAnnotationInspection())
    myFixture.addClass("""
      |package org.jetbrains.annotations; 
      |public class ApiStatus {  
      |  public @interface ScheduledForRemoval {
      |    String inVersion() default "";
      |  }
      |}""".trimMargin())
  }

  fun `test replace by attribute`() {
    myFixture.testHighlighting("ReplaceScheduledForRemovalByAttribute.java")
    myFixture.launchAction(myFixture.findSingleIntention("Replace"))
    myFixture.checkResultByFile("ReplaceScheduledForRemovalByAttribute_after.java")
  }

  fun `test remove`() {
    myFixture.testHighlighting("RemoveScheduledForRemoval.java")
    myFixture.launchAction(myFixture.findSingleIntention("Remove annotation"))
    myFixture.checkResultByFile("RemoveScheduledForRemoval_after.java")
  }

  fun `test do not higlight if version is specified`() {
    myFixture.testHighlighting("ScheduledForRemovalWithVersion.java")
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_9
  }
}