// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.VisibilityUtil;

/**
 * @author Danila Ponomarenko
 */
public class CreateGetterOrSetterTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).VISIBILITY = VisibilityUtil.ESCALATE_VISIBILITY;
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createGetterOrSetter";
  }
}