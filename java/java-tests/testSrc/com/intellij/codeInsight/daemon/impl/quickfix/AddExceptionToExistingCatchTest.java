// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddExceptionToExistingCatchTest extends LightIntentionActionTestCase {
  private static final Pattern CHOOSER_TEST_NAME = Pattern.compile(".+_Choose_(.+)\\.java");
  
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addExceptionToExistingCatch";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Matcher matcher = CHOOSER_TEST_NAME.matcher(getTestName(false));
    if (matcher.matches()) {
      UiInterceptors.register(new ChooserInterceptor(null, matcher.group(1)));
    }
  }
}
