// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodRecommenderInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testExtractMethodRecommender() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 10;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
  
  public void testImplicitClass() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testRedundantSuppression() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 20;
    myFixture.enableInspections(inspection, new RedundantSuppressInspection());
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  /**
   * Test that quickfix can extract if anchor is moved to the declaration
   */
  public void testCallExtract() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 20;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
    IntentionAction intention = myFixture.getAvailableIntention(JavaBundle.message("intention.extract.method.text"));
    assertNotNull(intention);
    intention.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }

  /**
   *  Based on {@link ExtractMethodRecommenderInspectionTest#testExtractMethodRecommender()}
   *  when the suggestion is placed on comments, even though this place is not really convenient.
   *  This method checks that quickfix works even for this place
   */
  public void testCallExtractFirstNotDeclaration() {
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 10;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
    IntentionAction intention = myFixture.getAvailableIntention(JavaBundle.message("intention.extract.method.text"));
    assertNotNull(intention);
    intention.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  public String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/extractMethodRecommender";
  } 
}
