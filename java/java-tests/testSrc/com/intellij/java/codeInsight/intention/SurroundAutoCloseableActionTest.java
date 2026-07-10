// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.java.JavaBundle;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/surroundAutoCloseable/")
public class SurroundAutoCloseableActionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/surroundAutoCloseable/";
  }

  public void testSimple() { doTest(); }
  public void testSimplePast() { doTest(); }
  public void testUsage() { doTest(); }
  public void testMixedUsages() { doTest(); }
  public void testLastDeclaration() { doTest(); }
  public void testSplitVar() { doTest(); }
  public void testExpression() { doTest(); }
  public void testExpressionIncomplete() { doTest(); }
  public void testUnrelatedVariable() { doTest(); }
  public void testCommentsInVarDeclaration() {
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_LOCALS = true;
    doTest();
  }
  public void testImplicitlyTypedDeclaration() { doTest(); }

  public void testNullTypeMovedVariable() { doTest(); }

  private void doTest() {
    String name = getTestName(false);
    String intention = JavaBundle.message("intention.surround.resource.with.ARM.block");
    CodeInsightTestUtil.doIntentionTest(myFixture, intention, name + ".java", name + "_after.java");
  }
}