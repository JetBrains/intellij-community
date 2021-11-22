// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.lang.java.JavaLanguage;

/**
 * @author ven
 */
public class CreateClassFromNewTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).SPACE_BEFORE_CLASS_LBRACE = true;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createClassFromNew";
  }
}
