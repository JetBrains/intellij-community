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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class ReloadFromDiskAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (project == null || editor == null) return;
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return;

    String message = IdeBundle.message("prompt.reload.file.from.disk", file.getVirtualFile().getPresentableUrl());
    int res = Messages.showOkCancelDialog(project, message, IdeBundle.message("title.reload.file"), Messages.getWarningIcon());
    if (res != Messages.OK) return;

    Runnable command = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            @Override
            public void run() {
              if (!project.isDisposed()) {
                file.getVirtualFile().refresh(false, false);
                PsiManager.getInstance(project).reloadFromDisk(file);
              }
            }
          }
        );
      }
    };
    CommandProcessor.getInstance().executeCommand(project, command, IdeBundle.message("command.reload.from.disk"), null);
  }

  @Override
  public void update(AnActionEvent event) {
    boolean enabled = false;

    Project project = event.getProject();
    Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());
    if (project != null && editor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null && file.getVirtualFile() != null) {
        enabled = true;
      }
    }

    event.getPresentation().setEnabled(enabled);
  }
}
