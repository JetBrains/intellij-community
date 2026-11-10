// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class TryWithIdenticalCatchesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig");
  }

  private static final String PATH = "com/siyeh/igtest/errorhandling/try_identical_catches/";

  @Test
  public void testTryIdenticalCatches() {
    doTest();
  }

  @Test
  public void testNonDisjunctTypes() {
    doTest();
  }

  @Test
  public void testMethodQualifier() {
    highlightTest(false, false);
  }

  @Test
  public void testIdenticalCatchUnrelatedExceptions() {
    doTest();
  }

  @Test
  public void testIdenticalCatchThreeOutOfFour() {
    doTest(true, false, false);
  }

  @Test
  public void testIdenticalCatchWithComments() {
    doTest();
  }

  @Test
  public void testIdenticalCatchWithEmptyComments() {
    doTest();
  }

  @Test
  public void testIdenticalCatchWithDifferentComments() {
    doTest(false, true, false);
  }

  @Test
  public void testIdenticalCatchDifferentCommentStyle() {
    doTest();
  }

  @Test
  public void testIdenticalCatchCommentsInDifferentPlaces() {
    doTest();
  }

  @Test
  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessAll() {
    doTest(true, true, false);
  }

  @Test
  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessOne() {
    doTest(false, true, false);
  }

  @Test
  public void testIdenticalNonemptyCatchWithDifferentCommentsStrict() {
    highlightTest(true, true);
  }

  @Test
  public void testCatchParameterRewritten() {
    highlightTest(false, false);
  }

  @Test
  public void testMoreCommonCatchPreserved() {
    doTest();
  }

  @Test
  public void testMoreCommonCatchPreservedWithOneLine() {
    doTest();
  }

  @Test
  public void testPreservedNewLine() {
    doTest();
  }

  public void doTest() {
    doTest(false, false, false);
  }

  private void doTest(boolean processAll, boolean checkInfos, boolean strictComments) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    highlightTest(checkInfos, strictComments);
    String name = myFixture.getTestName(false);
    IntentionAction intention;
    if (processAll) {
      intention = myFixture.findSingleIntention(
        InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                  InspectionGadgetsBundle.message("try.with.identical.catches.display.name")));
    }
    else {
      intention = myFixture.findSingleIntention(InspectionGadgetsBundle.message("try.with.identical.catches.quickfix"));
    }
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(PATH + name + ".after.java");
    });
  }

  private void highlightTest(boolean checkInfos, boolean strictComments) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    TryWithIdenticalCatchesInspection inspection = new TryWithIdenticalCatchesInspection();
    inspection.ignoreBlocksWithDifferentComments = strictComments;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.checkHighlighting(true, checkInfos, false);
  }
}
