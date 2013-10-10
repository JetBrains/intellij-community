/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.customFolding;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * @author Rustam Vishnyakov
 */
public class GotoCustomRegionAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (Boolean.TRUE.equals(e.getData(PlatformDataKeys.IS_MODAL_CONTEXT))) {
      return;
    }
    if (project != null && editor != null) {
      if (DumbService.getInstance(project).isDumb()) {
        DumbService.getInstance(project).showDumbModeNotification(IdeBundle.message("goto.custom.region.message.dumb.mode"));
        return;
      }
      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(
        project,
        new Runnable() {
          @Override
          public void run() {
            GotoCustomRegionDialog dialog = new GotoCustomRegionDialog(project, editor);
            dialog.show();
            if (dialog.isOK()) {
              PsiElement navigationElement = dialog.getNavigationElement();
              if (navigationElement != null) {
                navigateTo(editor, navigationElement);
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
              }
            }
          }
        },
        IdeBundle.message("goto.custom.region.command"),
        null);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setText("Custom Region...");
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final Project project = e.getProject();
    boolean isAvailable = editor != null && project != null;
    presentation.setEnabled(isAvailable);
    presentation.setVisible(isAvailable);
  }

  private static void navigateTo(Editor editor, PsiElement element) {
    int offset = element.getTextRange().getStartOffset();
    if (offset >= 0 && offset < editor.getDocument().getTextLength()) {
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      editor.getSelectionModel().removeSelection();
    }
  }
}
