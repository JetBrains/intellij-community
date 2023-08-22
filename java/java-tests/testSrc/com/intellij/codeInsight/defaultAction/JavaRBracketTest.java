package com.intellij.codeInsight.defaultAction;

import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaRBracketTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
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
