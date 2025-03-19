// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaBackspaceTest extends LightPlatformCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public void testBracket1() { doTest(); }

  public void testBracket2() { doTest(); }

  public void testBracket3() { doTest(); }

  public void testBracket4() { doTest(); }

  public void testIdea186011() { doTest(); }

  public void testQuote1() { doTest(); }

  public void testQuote2() { doTest(); }

  public void testQuoteAndCommentAfter() { doTest(); }

  protected void doTest() {
    @NonNls String path = "/codeInsight/backspace/";

    configureByFile(path + getTestName(false) + ".java");
    backspace();
    checkResultByFile(path + getTestName(false) + "_after.java");
  }
}

