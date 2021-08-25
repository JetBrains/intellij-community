// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this in actions or action groups to flag that their {@link AnAction#beforeActionPerformedUpdate(AnActionEvent)}
 * and {@link AnAction#actionPerformed(AnActionEvent)} methods are to be called when all documents are committed.<p></p>
 * <p>
 * Use this interface instead of calling {@link PsiDocumentManager#commitAllDocuments()} directly.
 */
public interface PerformWithDocumentsCommitted {
  default boolean isPerformWithDocumentsCommitted() {
    return true;
  }

  static boolean isPerformWithDocumentsCommitted(@NotNull AnAction action) {
    return action instanceof PerformWithDocumentsCommitted && ((PerformWithDocumentsCommitted)action).isPerformWithDocumentsCommitted();
  }

}
