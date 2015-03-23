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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class GotoLineAction extends AnAction implements DumbAware {
  public GotoLineAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Editor editor = e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    if (Boolean.TRUE.equals(e.getData(PlatformDataKeys.IS_MODAL_CONTEXT))) {
      GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
      dialog.show();
    }
    else {
      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(
          project, new Runnable(){
          public void run() {
            GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
            dialog.show();
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          }
        },
        IdeBundle.message("command.go.to.line"),
        null
      );
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    Editor editor = event.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    presentation.setEnabled(editor != null);
    presentation.setVisible(editor != null);
  }
}
