/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeHighlighting;

import com.intellij.openapi.progress.ProgressIndicator;
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
   *                 See also {@link com.intellij.openapi.progress.ProgressIndicator#checkCanceled()}.
   */
  void collectInformation(@NotNull ProgressIndicator progress);

  /**
   * Called to apply information collected by {@linkplain #collectInformation(com.intellij.openapi.progress.ProgressIndicator)} to the editor.
   * This method is called from the event dispatch thread.
   */
  void applyInformationToEditor();
}
