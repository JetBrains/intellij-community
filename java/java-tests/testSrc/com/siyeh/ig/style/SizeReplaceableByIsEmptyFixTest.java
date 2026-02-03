// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

public class SizeReplaceableByIsEmptyFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SizeReplaceableByIsEmptyInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igtest/style/size_replaceable_by_is_empty";
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/java/java-tests/testData/ig";
  }
}