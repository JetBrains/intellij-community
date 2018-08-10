// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.EditorGotoLineNumberDialog;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GotoLineAction extends AnAction implements DumbAware {
  public GotoLineAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Editor editor = e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    if (Boolean.TRUE.equals(e.getData(PlatformDataKeys.IS_MODAL_CONTEXT))) {
      GotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(project, editor);
      dialog.show();
    }
    else {
      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(
        project, () -> {
          GotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(project, editor);
          dialog.show();
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
        },
        IdeBundle.message("command.go.to.line"),
        null
      );
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event){
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
