// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.unusedImport.UnusedImportInspection;

public final class RemoveAllUnusedImportsTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedImportInspection());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/highlight/remove_imports";
  }
}
