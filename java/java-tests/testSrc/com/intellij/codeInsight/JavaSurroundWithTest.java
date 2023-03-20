// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfElseExpressionSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithRunnableSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class JavaSurroundWithTest extends LightJavaCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testTryCatch1() {
    doTest(new JavaWithTryCatchSurrounder());
  }

  public void testRunnable() {
    doTest(new JavaWithRunnableSurrounder());
  }

  public void testBlockedByPSI() {
    doTest(new JavaWithIfElseExpressionSurrounder());
  }

  private void doTest(Surrounder handler) {
    String baseName = getBaseName();
    configureByFile(baseName + "." + "java");
    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), handler);
    checkResultByFile(baseName + "_after." + "java");
  }

  private String getBaseName() {
    return "/codeInsight/surroundWith/" + getTestName(false);
  }
}

