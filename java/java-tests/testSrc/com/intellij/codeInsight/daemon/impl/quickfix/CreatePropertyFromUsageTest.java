// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class CreatePropertyFromUsageTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createPropertyFromUsage";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  @Override
  protected ActionHint parseActionHintImpl(@NotNull PsiFile psiFile, @NotNull String contents) {
    return ActionHint.parse(psiFile, contents, false);
  }
}
