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

package com.intellij.codeInsight.editorActions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class CutHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public CutHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getContentComponent()));
    if (project == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection() && !selectionModel.hasBlockSelection()) {
      selectionModel.selectLineAtCaret();
      if (!selectionModel.hasSelection()) return;
    }

    EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_COPY).execute(editor, dataContext);

    EditorModificationUtil.deleteSelectedText(editor);
  }
}
