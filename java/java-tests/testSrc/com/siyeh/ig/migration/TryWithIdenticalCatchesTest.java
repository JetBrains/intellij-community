// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;

public class TryWithIdenticalCatchesTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String PATH = "com/siyeh/igtest/errorhandling/try_identical_catches/";

  public void testTryIdenticalCatches() {
    doTest();
  }

  public void testNonDisjunctTypes() {
    doTest();
  }

  public void testMethodQualifier() {
    highlightTest(false, false);
  }

  public void testIdenticalCatchUnrelatedExceptions() {
    doTest();
  }

  public void testIdenticalCatchThreeOutOfFour() {
    doTest(true, false, false);
  }

  public void testIdenticalCatchWithComments() {
    doTest();
  }

  public void testIdenticalCatchWithEmptyComments() {
    doTest();
  }

  public void testIdenticalCatchWithDifferentComments() {
    doTest(false, true, false);
  }

  public void testIdenticalCatchDifferentCommentStyle() {
    doTest();
  }

  public void testIdenticalCatchCommentsInDifferentPlaces() {
    doTest();
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessAll() {
    doTest(true, true, false);
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsProcessOne() {
    doTest(false, true, false);
  }

  public void testIdenticalNonemptyCatchWithDifferentCommentsStrict() {
    highlightTest(true, true);
  }

  public void testCatchParameterRewritten() {
    highlightTest(false, false);
  }

  public void testMoreCommonCatchPreserved() {
    doTest();
  }

  public void testMoreCommonCatchPreservedWithOneLine() {
    doTest();
  }

  public void testPreservedNewLine() {
    doTest();
  }

  public void doTest() {
    doTest(false, false, false);
  }

  private void doTest(boolean processAll, boolean checkInfos, boolean strictComments) {
    highlightTest(checkInfos, strictComments);
    String name = getTestName(false);
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
    myFixture.launchAction(intention);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(PATH + name + ".after.java");
  }

  private void highlightTest(boolean checkInfos, boolean strictComments) {
    String name = getTestName(false);
    TryWithIdenticalCatchesInspection inspection = new TryWithIdenticalCatchesInspection();
    inspection.ignoreBlocksWithDifferentComments = strictComments;
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(PATH + name + ".java");
    myFixture.checkHighlighting(true, checkInfos, false);
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig";
  }
}
