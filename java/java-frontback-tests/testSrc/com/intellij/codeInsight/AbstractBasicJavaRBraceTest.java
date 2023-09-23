// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.codeInsight.defaultAction.DefaultActionTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaRBraceTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }


  public void testMethodClosingBrace() { doTest(); }

  protected void doTest() {
    String path = "/codeInsight/defaultAction/rbrace/";

    configureByFile(path + getTestName(false) + ".java");
    performAction('}');
    checkResultByFile(path + getTestName(false) + "_after.java");
  }

  public void testClosingNestedArrayInitializer() { doTest(); }
  public void testClosingArrayInitializer() { doTest(); }
}
