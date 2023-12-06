package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.codeInsight.defaultAction.DefaultActionTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicJavaRBracketTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }

  public void test1() { doTest(); }

  public void test2() { doTest(); }

  private void doTest() {
    String path = "/codeInsight/defaultAction/rbracket/";

    configureByFile(path + getTestName(false) + ".java");
    performAction(']');
    checkResultByFile(path + getTestName(false) + "_after.java");
  }
}
