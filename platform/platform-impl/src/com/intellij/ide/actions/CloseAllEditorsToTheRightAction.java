/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

import java.util.ArrayList;

/**
 * Created by vvv1559 on 07.08.2016.
 */
public class CloseAllEditorsToTheRightAction extends CloseEditorsActionBase {
  @Override
  protected boolean isFileToClose(EditorComposite editor, EditorWindow window) {
    return !window.isFilePinned(editor.getFile());
  }

  @Override
  protected String getPresentationText(boolean inSplitter) {
    if (inSplitter){
      return IdeBundle.message("action.close.all.editors.to.the.right.in.tab.group");
    } else {
      return IdeBundle.message("action.close.all.editors.to.the.right");
    }
  }

  @Override
  protected void fillFileListFromWindow(EditorWindow window, ArrayList<Pair<EditorComposite, EditorWindow>> consumer) {
    final EditorComposite [] editors = window.getEditors ();
    final EditorComposite selectedEditor = window.getSelectedEditor();
    boolean isSelectedEditorReached = false;
    for (final EditorComposite editor : editors) {
      if (isSelectedEditorReached && isFileToClose(editor, window)) {
        consumer.add(Pair.create(editor, window));
      }

      if (editor == selectedEditor){ isSelectedEditorReached = true; }
    }
  }
}
