// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.codeInsight.defaultAction.DefaultActionTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaLParenthTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public void test1() { doTest(); }
  public void test2() { doTest(); }
  public void test3() { doTest(); }
  public void test4() { doTest(); }
  public void test5() { doTest(); }
  public void test6() { doTest(); }

  private void doTest() {
    String path = "/codeInsight/defaultAction/lparenth/";

    configureByFile(path + getTestName(false) + ".java");
    performAction('(');
    checkResultByFile(path + getTestName(false) + "_after.java");
  }
}
