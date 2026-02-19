// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_22;

public class RenameToUnnamedVariableTest extends LightIntentionActionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_22;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/renameToUnnamed";
  }
}