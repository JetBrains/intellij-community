// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CleanupIntentionMenuContributor implements IntentionMenuContributor {
  @Override
  public void collectActions(@NotNull Editor hostEditor,
                             @NotNull PsiFile hostFile,
                             @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                             int passIdToShowIntentionsFor,
                             int offset) {
    boolean cleanup = appendCleanupCode(intentions.inspectionFixesToShow, hostFile);
    if (!cleanup) {
      appendCleanupCode(intentions.errorFixesToShow, hostFile);
    }
  }

  private static boolean appendCleanupCode(@NotNull List<HighlightInfo.IntentionActionDescriptor> actionDescriptors, @NotNull PsiFile psiFile) {
    for (HighlightInfo.IntentionActionDescriptor descriptor : actionDescriptors) {
      if (descriptor.canCleanup(psiFile)) {
        IntentionManager manager = IntentionManager.getInstance();
        actionDescriptors.add(new HighlightInfo.IntentionActionDescriptor(manager.createCleanupAllIntention(),
                                                                          manager.getCleanupIntentionOptions(),
                                                                          InspectionsBundle.message("action.description.code.cleanup.options"),
                                                                          null, null, null, null, null));
        return true;
      }
    }
    return false;
  }
}
