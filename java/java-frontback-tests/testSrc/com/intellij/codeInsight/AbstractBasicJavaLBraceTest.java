// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.codeInsight.defaultAction.DefaultActionTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaLBraceTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public void testArrayInitializer() { doTest(); }
  public void testArrayInitializerNoSpace() { doTest(); }
  public void testInsideCodeBlock() { doTest(); }
  public void testOutsideCodeBlock() { doTest(); }
  public void testArrayInitializerBegins() { doTest(); }
  public void testArrayInitializer1_5Style() { doTest(); }

  protected void doTest() {
    configureByFile(getTestPath() + ".java");
    performAction('{');
    checkResultByFile(getTestPath() + "_after.java");
  }

  private String getTestPath() {
    return "/codeInsight/defaultAction/lbrace/" + getTestName(false);
  }
}
