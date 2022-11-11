// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class CreateMethodFromUsageTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

  @Override
  protected ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents) {
    return ActionHint.parse(file, contents, false);
  }
}
