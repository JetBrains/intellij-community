// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.extractMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.java.JavaBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodRecommenderInspection;
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodService;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.utils.coroutines.CoroutinesTestUtilKt.waitCoroutinesBlocking;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ExtractMethodRecommenderInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/extractMethodRecommender");
  }

  @Test
  public void testExtractMethodRecommender() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 10;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testExtractMethodRecommenderComment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 100;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testRedundantSuppression() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 20;
    myFixture.enableInspections(inspection, new RedundantSuppressInspection());
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  /**
   * Test that quickfix can extract if anchor is moved to the declaration
   */
  @Test
  public void testCallExtract() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 20;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
    invokeExtractMethodIntention();
    myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
  }

  /**
   * Based on {@link ExtractMethodRecommenderInspectionTest#testExtractMethodRecommender()}
   * when the suggestion is placed on comments, even though this place is not really convenient.
   * This method checks that quickfix works even for this place
   */
  @Test
  public void testCallExtractFirstNotDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ExtractMethodRecommenderInspection inspection = new ExtractMethodRecommenderInspection();
    inspection.minLength = 10;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
    invokeExtractMethodIntention();
    myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
  }

  private void invokeExtractMethodIntention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntentionAction intention = myFixture.getAvailableIntention(JavaBundle.message("intention.extract.method.text"));
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      waitCoroutinesBlocking(ExtractMethodService.getInstance(myFixture.getProject()).getScope());
    });
  }
}
