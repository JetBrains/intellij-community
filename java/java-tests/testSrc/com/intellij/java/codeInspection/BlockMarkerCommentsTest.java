// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.BlockMarkerCommentsInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
public class BlockMarkerCommentsTest extends LightJavaCodeInsightFixtureTestCase {

  private BlockMarkerCommentsInspection myInspection;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/blockMarkerComments/";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInspection = new BlockMarkerCommentsInspection();
    myFixture.enableInspections(myInspection);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myFixture.disableInspections(myInspection);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myInspection = null;
      super.tearDown();
    }
  }

  private void doTestInspection() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  private void doTestQuickFix() {
    final String testFileName = getTestName(false);
    myFixture.enableInspections(new BlockMarkerCommentsInspection());
    myFixture.configureByFile(testFileName + ".java");
    final IntentionAction intentionAction = myFixture.findSingleIntention("Remove block marker comment");
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(testFileName + "_after.java", true);
  }

  public void testMethod() {
    doTestInspection();
  }

  public void testIf() {
    doTestInspection();
  }

  public void testLoop() {
    doTestInspection();
  }

  public void testTryCatch() {
    doTestInspection();
  }

  public void testClass() {
    doTestInspection();
  }

  public void testRemoveBlockMarker() {
    doTestQuickFix();
  }
}
