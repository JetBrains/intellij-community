// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.actions;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@SkipSlowTestLocally
@PerformanceUnitTest
@StressTestApplication
public class CreateMethodFromUsagePerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testWithHugeNumberOfParameters() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    Benchmark.newBenchmark("5000 args for a new method", () -> {
      String text = "class Foo {{ f<caret>oo(" + StringUtil.repeat("\"a\", ", 5000) + " \"a\");}}";
      daemon.configureFromFileText("Foo.java", text);
      daemon.doAction("Create method 'foo' in 'Foo'");
    })
      .start();
  }
}
