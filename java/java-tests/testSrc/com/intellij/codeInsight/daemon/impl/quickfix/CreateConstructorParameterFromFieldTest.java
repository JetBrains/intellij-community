// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.siyeh.ig.style.MissortedModifiersInspection;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;

public class CreateConstructorParameterFromFieldTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(), new MissortedModifiersInspection(), new UnqualifiedFieldAccessInspection());
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    if (getTestName(false).contains("SameParameter")) {
      settings.PREFER_LONGER_NAMES = false;
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorParameterFromField";
  }
}
