// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;

@SkipSlowTestLocally
public class CreateMethodFromUsagePerformanceTest extends LightQuickFixTestCase {

  public void testWithHugeNumberOfParameters() {
    PlatformTestUtil.startPerformanceTest("5000 args for a new method", 400_000, () -> {
      String text = "class Foo {{ f<caret>oo(" + StringUtil.repeat("\"a\", ", 5000) + " \"a\");}}";
      configureFromFileText("Foo.java", text);
      doAction("Create method 'foo' in 'Foo'");
    })
      .assertTiming();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

}
