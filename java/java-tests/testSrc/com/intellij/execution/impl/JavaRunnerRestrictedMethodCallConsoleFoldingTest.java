// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.JavaRunnerRestrictedMethodCallConsoleFolding;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class JavaRunnerRestrictedMethodCallConsoleFoldingTest extends LightJavaCodeInsightFixtureTestCase {
  public void testFold() {
    String actualText = """
      WARNING: A restricted method in java.lang.System has been called
      WARNING: java.lang.System::load has been called by com.intellij.rt.execution.application.AppMainV2 in an unnamed module (file:/C:/projects-idea/intellij/out/classes/production/intellij.java.rt/)
      WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
      WARNING: Restricted methods will be blocked in a future release unless native access is enabled
      """;
    List<String> lines = StringUtil.split(actualText, "\n");
    JavaRunnerRestrictedMethodCallConsoleFolding consoleFolding = new JavaRunnerRestrictedMethodCallConsoleFolding();
    for (String line : lines) {
      assertTrue(consoleFolding.shouldFoldLine(getProject(), line));
    }
    assertNotNull(consoleFolding.getPlaceholderText(getProject(), lines));
  }

  public void testNotFold() {
    String actualText = """
      WARNING: A restricted method in java.lang.System has been called
      WARNING: java.lang.System::load has been called by com.intellij.rt.execution.application.AppMainV in an unnamed module (file:/C:/projects-idea/intellij/out/classes/production/intellij.java.rt/)
      WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
      WARNING: Restricted methods will be blocked in a future release unless native access is enabled
      """;
    List<String> lines = StringUtil.split(actualText, "\n");
    JavaRunnerRestrictedMethodCallConsoleFolding consoleFolding = new JavaRunnerRestrictedMethodCallConsoleFolding();
    assertNull(consoleFolding.getPlaceholderText(getProject(), lines));
  }
}