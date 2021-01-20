// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ConvertToBasicLatinInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class ConvertToBasicLatinTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/convertToBasicLatin/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ConvertToBasicLatinInspection());
  }

  public void testCharLiteral() { doTest(); }
  public void testStringLiteral() { doTest(); }
  public void testPlainComment() { doTest(); }
  public void testDocComment() { doTest(); }
  public void testDocTag() { doTest(); }
  public void testUnclosedStringLiteral() { doTest(); }
  public void testUnclosedPlainComment() { doTest(); }
  public void testUnclosedDocComment() { doTest(); }

  private void doTest() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(JavaBundle.message("inspection.convert.to.basic.latin"));
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }
}