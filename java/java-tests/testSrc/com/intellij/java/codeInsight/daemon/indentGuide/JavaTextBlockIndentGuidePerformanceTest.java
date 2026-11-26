// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@PerformanceUnitTest
@StressTestApplication
public class JavaTextBlockIndentGuidePerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testManyCodeBlocks() {
    int n = 2000;
    int nLines = 20;
    String text = "class X {\n" +
                  createCodeBlocks(n, nLines) +
                  "\n}";
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    Benchmark.newBenchmark(daemon.getFixture().getTestName(false), daemon::doHighlighting)
      .setup(() -> daemon.configureFromFileText("X.java", text))
      .start();
  }

  private static String createCodeBlocks(int n, int nLines) {
    return IntStream.range(0, n)
      .mapToObj(i -> "  String codeBlock" + i + " = ")
      .map(decl -> decl + "\"\"\"\n" + String.join("\n", Collections.nCopies(nLines, " 0")) + "\n \"\"\";")
      .collect(Collectors.joining("\n"));
  }
}
