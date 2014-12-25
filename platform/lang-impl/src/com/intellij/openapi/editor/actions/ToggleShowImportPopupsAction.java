/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ToggleShowImportPopupsAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    PsiFile file = getFile(e);
    return file != null && DaemonCodeAnalyzer.getInstance(file.getProject()).isImportHintsEnabled(file);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    PsiFile file = getFile(e);
    if (file != null) {
      DaemonCodeAnalyzer.getInstance(file.getProject()).setImportHintsEnabled(file, state);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean works = getFile(e) != null;
    e.getPresentation().setEnabled(works);
    e.getPresentation().setVisible(works);
    super.update(e);
  }

  @Nullable
  private static PsiFile getFile(AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return editor == null ? null : e.getData(CommonDataKeys.PSI_FILE);
  }
}
