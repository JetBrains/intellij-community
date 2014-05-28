/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
public class BlockMarkerCommentsTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/blockMarkerComments/";
  }

  private void doTestInspection() {
    myFixture.testInspection(getTestName(true), new LocalInspectionToolWrapper(new BlockMarkerCommentsInspection()));
  }

  private void doTestQuickFix() {
    final String testFileName = getTestName(true);
    myFixture.enableInspections(new BlockMarkerCommentsInspection());
    myFixture.configureByFile(testFileName + ".java");
    final IntentionAction intentionAction = myFixture.findSingleIntention("Remove block marker comments");
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
