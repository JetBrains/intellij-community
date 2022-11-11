// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

/**
 * @author Pavel.Dolgov
 */
public class ReplaceIteratorForEachLoopWithIteratorForLoopFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).startsWith("Final")) {
      final JavaCodeStyleSettings codeStyleSettings =
        JavaCodeStyleSettings.getInstance(getProject());
      codeStyleSettings.GENERATE_FINAL_LOCALS = true;
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceIteratorForEachWithFor";
  }
}
