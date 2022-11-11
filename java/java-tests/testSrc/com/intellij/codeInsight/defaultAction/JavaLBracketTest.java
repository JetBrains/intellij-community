// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaLBracketTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() { doTest(); }

  public void test2() { doTest(); }

  public void test3() { doTest(); }

  private void doTest() {
    String path = "/codeInsight/defaultAction/lbracket/";

    configureByFile(path + getTestName(false) + ".java");
    performAction('[');
    checkResultByFile(path + getTestName(false) + "_after.java");
  }
}
