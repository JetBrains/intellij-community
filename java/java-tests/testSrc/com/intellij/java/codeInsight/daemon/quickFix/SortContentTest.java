// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

public class SortContentTest extends LightIntentionActionTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/sortContent";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getCurrentCodeStyleSettings().FORMATTER_TAGS_ENABLED = true;
  }
}

