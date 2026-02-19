// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.intentions;

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditIntentionActionFilter implements IntentionActionFilter {
  @Override
  public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile psiFile) {
    if (psiFile != null && LightEdit.owns(psiFile.getProject())) {
      IntentionAction originalIntentionAction = getOriginalAction(intentionAction);
      return originalIntentionAction instanceof LightEditCompatible;
    }
    return true;
  }

  private static @NotNull IntentionAction getOriginalAction(@NotNull IntentionAction intentionAction) {
    if (intentionAction instanceof IntentionActionWrapper) {
      return ((IntentionActionWrapper)intentionAction).getDelegate();
    }
    return intentionAction;
  }
}
