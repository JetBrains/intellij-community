// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

public class BindFieldsFromParametersTest extends LightIntentionActionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.FIELD_NAME_PREFIX = "my";
    assertTrue(settings.PREFER_LONGER_NAMES);
    if (getTestName(false).contains("SameParam")) {
      settings.PREFER_LONGER_NAMES = false;
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/bindFieldsFromParameters";
  }
}
