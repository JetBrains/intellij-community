// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;

import java.util.List;

public class ExpandStaticImportActionTest extends LightIntentionActionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).contains("Multiple")) {
      UiInterceptors.register(new ChooserInterceptor(List.of("Replace all and delete the import",
                                                             "Replace this occurrence and keep the import"),
                                                     "Replace all and delete the import"));
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/expandStaticImport";
  }
}
