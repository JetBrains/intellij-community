// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractJavadocTypedHandlerFunctionalTest extends LightPlatformCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/javadocTypedHandler/";

  public void testEmptyTag() {
    doTest('>');
  }

  public void testComment() {
    doTest('>');
  }

  public void testCodeTag() {
    doTest('>');
  }
  
  public void testTypeParam() {
    doTest('>');
  }

  public void testDocTagStart() {
    doTest('@');
  }

  public void testStartMarkdownComment() {
    doTest('/');
  }

  private void doTest(char typedChar) {
    String testName = getTestName(true);
    configureByFile(BASE_PATH + testName + ".java");
    type(typedChar);
    checkResultByFile(BASE_PATH + testName + "_after.java");
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathJavaTestUtil.getCommunityJavaTestDataPath();
  }
}
