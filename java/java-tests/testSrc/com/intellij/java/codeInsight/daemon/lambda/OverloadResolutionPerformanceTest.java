// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NonNls;

public class OverloadResolutionPerformanceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/overloadResolution";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testManyOverloadsWithVarargsPerformance() {
    Benchmark.newBenchmark("Overload resolution with 14 overloads", this::doTest).start();
  }

  public void testConstructorOverloadsWithDiamondsPerformance() {
    Benchmark.newBenchmark("Overload resolution with chain constructor calls with diamonds", this::doTest).start();
  }

  private void doTest() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}
