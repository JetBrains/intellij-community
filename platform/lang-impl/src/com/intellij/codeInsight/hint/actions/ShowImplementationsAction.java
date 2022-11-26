// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.ImplementationViewSession;
import com.intellij.codeInsight.hint.ImplementationViewSessionFactory;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShowImplementationsAction extends ShowRelatedElementsActionBase {
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE = "codeassists.quickdefinition.lookup";
  @NonNls public static final String CODEASSISTS_QUICKDEFINITION_FEATURE = "codeassists.quickdefinition";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  @NotNull
  protected List<ImplementationViewSessionFactory> getSessionFactories() {
    return ImplementationViewSessionFactory.EP_NAME.getExtensionList();
  }

  @Override
  @NotNull
  protected String getPopupTitle(@NotNull ImplementationViewSession session) {
    return CodeInsightBundle.message("implementation.view.title", session.getText());
  }

  @Override
  protected boolean couldPinPopup() {
    return true;
  }

  @Override
  protected void triggerFeatureUsed(@NotNull Project project) {
    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE);
    }
  }

  @Override
  @NotNull
  protected String getIndexNotReadyMessage() {
    return CodeInsightBundle.message("show.implementations.index.not.ready");
  }
}
