// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.bugs.EqualsOnSuspiciousObjectInspection;
import org.jetbrains.annotations.NotNull;

public class EqualsOnSuspiciousObjectInspectionFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new EqualsOnSuspiciousObjectInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igfixes/bugs/equals_on_suspicious_object";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/";
  }
}
