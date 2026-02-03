// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this in actions or action groups to flag that their
 * {@link AnAction#actionPerformed(AnActionEvent)} method is to be called when all documents are committed.<p></p>
 * <p>
 * Prefer calling {@link PsiDocumentManager#commitAllDocuments()} in {@link AnAction#actionPerformed(AnActionEvent)}.
 */
@ApiStatus.Obsolete
public interface PerformWithDocumentsCommitted {
  default boolean isPerformWithDocumentsCommitted() {
    return true;
  }

  static boolean isPerformWithDocumentsCommitted(@NotNull AnAction action) {
    return action instanceof PerformWithDocumentsCommitted && ((PerformWithDocumentsCommitted)action).isPerformWithDocumentsCommitted();
  }

  static void commitDocumentsIfNeeded(@NotNull AnAction action, @NotNull AnActionEvent event) {
    if (!isPerformWithDocumentsCommitted(action)) return;
    Project project = event.getProject();
    if (project == null) return;
    try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
  }
}
