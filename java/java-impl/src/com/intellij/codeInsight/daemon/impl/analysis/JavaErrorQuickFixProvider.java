// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class JavaErrorQuickFixProvider implements ErrorQuickFixProvider, DumbAware {
  @Override
  public void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo.Builder info) {
    if (!(errorElement.getLanguage() instanceof JavaLanguage)) return;
    Consumer<CommonIntentionAction> sink = action -> info.registerFix(action.asIntention(), null, null, null, null);
    var error = JavaErrorKinds.SYNTAX_ERROR.create(errorElement);
    DumbService.getDumbAwareExtensions(errorElement.getProject(), JavaErrorFixProvider.EP_NAME)
        .forEach(provider -> provider.registerFixes(error, sink));
  }
}
