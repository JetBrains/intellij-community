// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class RemoveAssignmentFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testAssignmentOfLocalVariable() {
    doTest();
  }

  public void testAssignmentOfMemberField() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.enableInspections(DefUseInspection.class);
    final IntentionAction intention = myFixture.findSingleIntention(JavaBundle.message("inspection.unused.assignment.remove.assignment.quickfix"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInspection/removeAssignmentFix";
  }
}
