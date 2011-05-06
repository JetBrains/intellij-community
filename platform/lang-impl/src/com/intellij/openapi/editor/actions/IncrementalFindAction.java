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

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public class IncrementalFindAction extends EditorAction {
  public static class Handler extends EditorActionHandler {

    private boolean myReplace;

    public Handler(boolean isReplace) {

      myReplace = isReplace;
    }

    public void execute(final Editor editor, DataContext dataContext) {
      final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      if (!editor.isOneLineMode()) {
        final JComponent headerComponent = editor.getHeaderComponent();
        if (headerComponent instanceof EditorSearchComponent) {
          EditorSearchComponent editorSearchComponent = (EditorSearchComponent)headerComponent;
            headerComponent.requestFocus();
          FindUtil.configureFindModel(myReplace, editor.getSelectionModel().getSelectedText(), editorSearchComponent.getFindModel());
        } else {
          FindManager findManager = FindManager.getInstance(project);
          FindModel model;
          if (myReplace) {
            model = findManager.createReplaceInFileModel();
          } else {
            model = new FindModel();
            model.copyFrom(findManager.getFindInFileModel());
          }
          FindUtil.configureFindModel(myReplace, editor.getSelectionModel().getSelectedText(), model);
          final EditorSearchComponent header = new EditorSearchComponent(editor, project, model);
          editor.setHeaderComponent(header);
          header.requestFocus();
        }
      }
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      return project != null && !editor.isOneLineMode();
    }
  }

  public IncrementalFindAction() {
    super(new Handler(false));
  }
}