// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface to provide tracking functionality for intention action events
 */
@ApiStatus.Experimental
public interface EventTrackingIntentionAction {

  /**
   * A notification for the intention action being shown to the user in some UI
   * This method can be invoked from any thread.
   */
  void suggestionShown(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile);
}
