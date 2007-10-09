
/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public abstract class DaemonCodeAnalyzer implements ProjectComponent {
  public static DaemonCodeAnalyzer getInstance(Project project) {
    return project.getComponent(DaemonCodeAnalyzer.class);
  }

  public abstract void settingsChanged();

  public abstract void updateVisibleHighlighters(Editor editor);

  public abstract void setUpdateByTimerEnabled(boolean value);

  public abstract boolean isHighlightingAvailable(PsiFile file);

  public abstract void setImportHintsEnabled(PsiFile file, boolean value);
  public abstract void resetImportHintsEnabledForProject(); 
  public abstract void setHighlightingEnabled(PsiFile file, boolean value);
  public abstract boolean isImportHintsEnabled(PsiFile file);
  public abstract boolean isAutohintsAvailable(PsiFile file);

  /**
   * Force restart
   */ 
  public abstract void restart();

  public abstract void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file);
}
