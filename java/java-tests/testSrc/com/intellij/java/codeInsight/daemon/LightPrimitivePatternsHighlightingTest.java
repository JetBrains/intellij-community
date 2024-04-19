// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightPrimitivePatternsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatterns";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    //todo change after new Java
    return JAVA_X;
  }

  public void testRecordPrimitiveInstanceOfPattern() {
    doTest();
  }

  public void testSimplePrimitiveInstanceOf() {
    doTest();
  }

  public void testSimplePrimitiveInstanceOfPattern() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}