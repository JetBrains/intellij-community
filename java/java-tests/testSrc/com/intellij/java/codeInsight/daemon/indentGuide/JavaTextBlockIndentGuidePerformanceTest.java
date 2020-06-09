// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class JavaTextBlockIndentGuidePerformanceTest extends LightDaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testManyCodeBlocks() {
    int n = 2000;
    int nLines = 20;
    String text = "class X {\n" +
                  createCodeBlocks(n, nLines) +
                  "\n}";
    PlatformTestUtil.startPerformanceTest(getTestName(false), 7500, this::doHighlighting)
      .setup(() -> configureFromFileText("X.java", text))
      .usesAllCPUCores()
      .assertTiming();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_14;
  }

  private static String createCodeBlocks(int n, int nLines) {
    return IntStream.range(0, n)
      .mapToObj(i -> "  String codeBlock" + i + " = ")
      .map(decl -> decl + "\"\"\"\n" + String.join("\n", Collections.nCopies(nLines, " 0")) + "\n \"\"\";")
      .collect(Collectors.joining("\n"));
  }
}
