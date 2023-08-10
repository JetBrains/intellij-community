// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaRParenthTest extends DefaultActionTestCase {
  private static final String BASE_PATH = "/codeInsight/defaultAction/rparenth/";

  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() { doTest(); }

  public void test2() { doTest(); }

  public void test3() { doTest(); }

  public void test4() { doTest(); }

  public void test5() { doTest(); }

  public void test6() { doTest(); }

  private void doTest() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    performAction(')');
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
