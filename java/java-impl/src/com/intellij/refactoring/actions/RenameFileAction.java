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
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.PsiElementRenameHandler;

/**
 * @author ven
 */
public class RenameFileAction extends AnAction implements DumbAware {
  public static final String RENAME_FILE = "Rename File...";

  public void actionPerformed(final AnActionEvent e) {
    final PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    assert file != null;
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    PsiElementRenameHandler.invoke(file, project, file, null);
  }

  public void update(AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    Presentation presentation = e.getPresentation();
    boolean enabled = file instanceof PsiClassOwner && e.getPlace() != ActionPlaces.EDITOR_POPUP && e.getData(CommonDataKeys.PROJECT) != null;
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
    if (enabled) {
      presentation.setText(RENAME_FILE);
      presentation.setDescription("Rename selected file");
    }
  }
}
