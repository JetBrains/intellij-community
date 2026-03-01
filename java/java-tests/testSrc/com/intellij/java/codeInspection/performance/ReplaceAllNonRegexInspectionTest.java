// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.performance;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.performance.ReplaceAllNonRegexInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class ReplaceAllNonRegexInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ReplaceAllNonRegexInspection();
  }

  public void testSimplePositive() {
    doTest();
    checkQuickFixAll();
  }

  public void testNonConstant() {
    doTest();
  }

  public void testRegexLike() {
    doTest();
  }

  public void testComment() {
    doTest();
    checkQuickFixAll();
  }

  public void testJava4() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_4, () -> doTest());
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/replaceAllNonRegex";
  }
}