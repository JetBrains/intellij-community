// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@PerformanceUnitTest
public class JavaTextBlockIndentGuidePerformanceTest extends LightDaemonAnalyzerTestCase {

  public void testManyCodeBlocks() {
    int n = 2000;
    int nLines = 20;
    String text = "class X {\n" +
                  createCodeBlocks(n, nLines) +
                  "\n}";
    Benchmark.newBenchmark(getTestName(false), this::doHighlighting)
      .setup(() -> configureFromFileText("X.java", text))
      .start();
  }

  private static String createCodeBlocks(int n, int nLines) {
    return IntStream.range(0, n)
      .mapToObj(i -> "  String codeBlock" + i + " = ")
      .map(decl -> decl + "\"\"\"\n" + String.join("\n", Collections.nCopies(nLines, " 0")) + "\n \"\"\";")
      .collect(Collectors.joining("\n"));
  }
}
