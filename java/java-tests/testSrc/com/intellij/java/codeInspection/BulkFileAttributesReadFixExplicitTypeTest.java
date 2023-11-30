// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.refactoring.JavaRefactoringSettings;

public class BulkFileAttributesReadFixExplicitTypeTest extends BulkFileAttributesReadFixJava11Test {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/bulkFileAttributesRead";
  }
}
