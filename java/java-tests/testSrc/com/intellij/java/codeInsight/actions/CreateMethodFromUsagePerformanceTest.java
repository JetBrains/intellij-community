// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.actions;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

@SkipSlowTestLocally
public class CreateMethodFromUsagePerformanceTest extends LightQuickFixTestCase {

  public void testWithHugeNumberOfParameters() {
    Benchmark.newBenchmark("5000 args for a new method", () -> {
      String text = "class Foo {{ f<caret>oo(" + StringUtil.repeat("\"a\", ", 5000) + " \"a\");}}";
      configureFromFileText("Foo.java", text);
      doAction("Create method 'foo' in 'Foo'");
    })
      .start();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

}
