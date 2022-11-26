/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public interface HintAction extends IntentionAction {
  /**
   * Show a popup or perform an automatic action in the given editor. Invoked for the visible highlighting results (e.g. errors or warnings)
   * on UI thread after the background highlighting is finished, without a write action.
   * Before the invocation, {@link #isAvailable(Project, Editor, PsiFile)} is checked to be {@code true}.
   * @return whether anything user-visible happened: a popup was shown or anything has changed in document/PSI/project model
   */
  boolean showHint(@NotNull Editor editor);

  /**
   * Perform this action if it doesn't require any user interaction and doesn't show any popups. Example: insert a new unambiguous import
   * for the reference that this intention or quick fix was created for.
   * This method is invoked on UI thread after the highlighting is finished, without a write action.
   * Before the invocation, {@link #isAvailable(Project, Editor, PsiFile)} is checked to be {@code true}.
   * @return whether the action was performed and anything has changed in document/PSI/project model
   * @deprecated Use {@link com.intellij.codeInsight.daemon.ReferenceImporter} instead, which does a better job to avoid freezes
   */
  @Deprecated
  default boolean fixSilently(@NotNull Editor editor) {
    return false;
  }
}
