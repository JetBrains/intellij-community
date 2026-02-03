// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantRecordConstructorInspection;
import org.jetbrains.annotations.NotNull;

public class RedundantRecordConstructorInspectionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new RedundantRecordConstructorInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/redundantRecordConstructor/";
  }
}
