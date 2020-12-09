// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeHighlighting;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

/**
 * Pass performs analysis in background and highlights found issues in the editor.
 */
public interface HighlightingPass {
  HighlightingPass[] EMPTY_ARRAY = new HighlightingPass[0];

  /**
   * Asks this pass to start analysis and hold collected information.
   * This method is called from a background thread.
   *
   * @param progress to check if highlighting process is cancelled. Pass is to check progress.isCanceled() as often as possible and
   *                 throw {@link com.intellij.openapi.progress.ProcessCanceledException} if {@code true} is returned.
   *                 See also {@link ProgressIndicator#checkCanceled()}.
   */
  void collectInformation(@NotNull ProgressIndicator progress);

  /**
   * Called to apply information collected by {@linkplain #collectInformation(ProgressIndicator)} to the editor.
   * This method is called from the event dispatch thread.
   */
  void applyInformationToEditor();

  default Condition<?> getExpiredCondition() {
    return Conditions.alwaysFalse();
  }
}
