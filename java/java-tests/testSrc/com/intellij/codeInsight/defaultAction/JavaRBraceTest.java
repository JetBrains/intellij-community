// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaRBraceTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() { doTest(); }

  public void testMethodClosingBrace() { doTest(); }
  public void testClosingBraceReformatsBlock() { doTest(); }

  private void doTest() {
    String path = "/codeInsight/defaultAction/rbrace/";

    configureByFile(path + getTestName(false) + ".java");
    performAction('}');
    checkResultByFile(path + getTestName(false) + "_after.java");
  }

  public void testClosingNestedArrayInitializer() { doTest(); }
  public void testClosingArrayInitializer() { doTest(); }
}
