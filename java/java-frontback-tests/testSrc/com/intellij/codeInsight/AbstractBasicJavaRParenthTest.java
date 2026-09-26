// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.codeInsight.defaultAction.DefaultActionTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaRParenthTest extends DefaultActionTestCase {
  private static final String BASE_PATH = "/codeInsight/defaultAction/rparenth/";

  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public void test1() { doTest(); }

  public void test2() { doTest(); }

  public void test3() { doTest(); }

  public void test4() { doTest(); }

  public void test5() { doTest(); }

  private void doTest() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    performAction(')');
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
