// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.codeInsight.AbstractEnterActionTestCase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

public class JavaEnterActionPerformanceTest extends AbstractEnterActionTestCase {
  public void testPerformance() {
    configureByFile("/codeInsight/enterAction/Performance.java");
    Benchmark.newBenchmark("enter in " + getFile(), () -> {
      performAction();
      deleteLine();
      caretUp();
    }).start();
  }


  public void testEnterPerformanceAfterDeepTree() {
    configureFromFileText("a.java", ("class Foo {\n" +
                                     "  {\n" +
                                     "    u." +
                                     StringUtil.repeat("\n      a('b').c(new Some()).", 500)) + "<caret>\n" +
                                    "      x(); } }");
    Benchmark.newBenchmark("enter", this::performAction).start();
  }
}