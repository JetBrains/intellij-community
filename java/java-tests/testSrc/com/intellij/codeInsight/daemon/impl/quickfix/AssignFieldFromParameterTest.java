// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

/**
 * @author ven
 */
public class AssignFieldFromParameterTest extends LightIntentionActionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).FIELD_NAME_PREFIX = "my";
    JavaCodeStyleSettings.getInstance(getProject()).STATIC_FIELD_NAME_PREFIX = "our";
    enableInspectionTool(new UnusedDeclarationInspectionBase());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/assignFieldFromParameter";
  }
}
