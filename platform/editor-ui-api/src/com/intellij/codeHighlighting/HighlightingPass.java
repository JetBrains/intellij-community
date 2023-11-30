// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

/**
 * HighlightingPass performs analysis in the background and highlights found issues in the editor.
 * <p>
 * Implement {@link com.intellij.openapi.project.DumbAware} to allow highlighting during index updates.
 * If pass is created by {@link TextEditorHighlightingPassFactory},
 * the factory should implement {@link com.intellij.openapi.project.DumbAware} as well
 */
public interface HighlightingPass {
  HighlightingPass[] EMPTY_ARRAY = new HighlightingPass[0];

  /**
   * Asks this pass to start analysis and hold collected information.
   * This method is called from a background thread.
   *
   * @param progress The progress indicator under which the current highlighting process is being performed.
   *                 The pass has to call {@code ProgressManager#checkCanceled} as often as possible (to
   *                 throw {@link com.intellij.openapi.progress.ProcessCanceledException} if some {@link ProgressIndicator} is canceled).
   *                 See also {@link ProgressIndicator#checkCanceled()}.
   */
  void collectInformation(@NotNull ProgressIndicator progress);

  /**
   * Called to apply information collected by {@linkplain #collectInformation(ProgressIndicator)} to the editor.
   * This method is called from the event dispatch thread.
   */
  void applyInformationToEditor();

  default @NotNull Condition<?> getExpiredCondition() {
    return Conditions.alwaysFalse();
  }
}
