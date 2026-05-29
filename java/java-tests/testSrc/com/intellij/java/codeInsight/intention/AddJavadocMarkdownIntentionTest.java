// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class AddJavadocMarkdownIntentionTest extends LightIntentionActionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getCommonSettings(JavaLanguage.INSTANCE).DOCUMENTATION_LINE_COMMENT_PREFERRED = true;
    CodeStyle.setTemporarySettings(getProject(), settings);
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/addJavadocMarkdown";
  }
}
