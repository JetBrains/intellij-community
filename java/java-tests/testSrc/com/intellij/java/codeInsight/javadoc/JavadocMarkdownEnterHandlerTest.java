// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import org.jetbrains.annotations.NotNull;

public class JavadocMarkdownEnterHandlerTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/javadoc/markdown/enterhandler";
  }

  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    executeAction("EditorEnter");

    checkResult(testName);
  }

  private void checkResult(@NotNull final String testName) {
    final String expectedFilePath = getBasePath() + "/after" + testName;
    checkResultByFile("In file: " + expectedFilePath, expectedFilePath, false);
  }
}
