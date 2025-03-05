// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.CommentByLineTestBase;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

public class JavaCommentByLinePerformanceTest extends CommentByLineTestBase {
  public void testUncommentLargeFilePerformance() {
    StringBuilder source = new StringBuilder("class C {\n");
    for (int i = 0; i < 5000; i++) {
      source.append("    int value").append(i).append(";\n");
    }
    source.append("}");
    configureFromFileText(getTestName(false) + ".java", source.toString());
    executeAction(IdeActions.ACTION_SELECT_ALL);
    Benchmark.newBenchmark("Uncommenting large file", CommentByLineTestBase::performAction)
      .setup(CommentByLineTestBase::performAction)
      .start();
  }
}
