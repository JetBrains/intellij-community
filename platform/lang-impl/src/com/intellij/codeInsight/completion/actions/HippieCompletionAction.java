// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class HippieCompletionAction extends BaseCodeInsightAction implements DumbAware {
  public HippieCompletionAction() {
    super(false);
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformedImpl(@NotNull Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.hippie");
    super.actionPerformedImpl(project, editor);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new HippieWordCompletionHandler(true);
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }
}
