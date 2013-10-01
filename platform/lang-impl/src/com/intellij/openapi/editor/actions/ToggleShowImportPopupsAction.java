/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ToggleShowImportPopupsAction extends ToggleAction {

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getAnalyzer(e).isImportHintsEnabled(getFile(e));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getAnalyzer(e).setImportHintsEnabled(getFile(e), state);
  }

  @Override
  public void update(AnActionEvent e) {
    if (getFile(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      super.update(e);
    }
  }

  private DaemonCodeAnalyzer getAnalyzer(AnActionEvent e) {
    return DaemonCodeAnalyzer.getInstance(e.getData(CommonDataKeys.PROJECT));
  }

  @Nullable
  private static PsiFile getFile(AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    return editor == null ? null : e.getData(CommonDataKeys.PSI_FILE);
  }
}
