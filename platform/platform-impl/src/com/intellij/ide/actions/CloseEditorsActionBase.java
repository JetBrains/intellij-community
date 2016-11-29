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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatusManager;

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class CloseEditorsActionBase extends AnAction implements DumbAware {
  protected ArrayList<Pair<EditorComposite, EditorWindow>> getFilesToClose (final AnActionEvent event) {
    final ArrayList<Pair<EditorComposite, EditorWindow>> res = new ArrayList<>();
    final DataContext dataContext = event.getDataContext();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    final EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(dataContext);
    final EditorWindow[] windows;
    if (editorWindow != null){
      windows = new EditorWindow[]{ editorWindow };
    }
    else {
      windows = editorManager.getWindows ();
    }
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    if (fileStatusManager != null) {
      for (int i = 0; i != windows.length; ++ i) {
        final EditorWindow window = windows [i];
        final EditorComposite [] editors = window.getEditors ();
        for (final EditorComposite editor : editors) {
          if (isFileToClose(editor, window)) {
            res.add(Pair.create(editor, window));
          }
        }
      }
    }
    return res;
  }

  protected abstract boolean isFileToClose(EditorComposite editor, EditorWindow window);

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, () -> {
        final ArrayList<Pair<EditorComposite, EditorWindow>> filesToClose = getFilesToClose (e);
        for (int i = 0; i != filesToClose.size (); ++ i) {
          final Pair<EditorComposite, EditorWindow> we = filesToClose.get(i);
          we.getSecond ().closeFile (we.getFirst ().getFile ());
        }
      }, IdeBundle.message("command.close.all.unmodified.editors"), null
    );
  }

  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(dataContext);
    final boolean inSplitter = editorWindow != null && editorWindow.inSplitter();
    presentation.setText(getPresentationText(inSplitter));
    final Project project = event.getData(CommonDataKeys.PROJECT);
    boolean enabled = (project != null && isActionEnabled(project, event));
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }

  protected boolean isActionEnabled(final Project project, final AnActionEvent event) {
    return getFilesToClose(event).size() > 0;
  }

  protected abstract String getPresentationText(boolean inSplitter);
}
