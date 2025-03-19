// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightUnnamedVariablesHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingUnnamed";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_22;
  }

  public void testUnnamedVariables() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> doTest());
  }

  public void testUnnamedVariablesJava22() {
    doTest();
  }

  public void testUnnamedVariablesInGuard() {
    doTest();
  } 

  public void testUnnamedVariablesJava9() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_9, () -> doTest());
  } 

  public void testUnnamedVariablesJava8() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  } 

  public void testUnnamedVariablesJava7() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_7, () -> doTest());
  } 

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}