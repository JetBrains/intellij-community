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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.Set;

/**
 * @author yole
 */
public class CloseAllUnpinnedEditorsAction extends CloseEditorsActionBase {

  @Override
  protected boolean isFileToClose(final EditorComposite editor, final EditorWindow window) {
    return !window.isFilePinned(editor.getFile());
  }

  @Override
  protected String getPresentationText(final boolean inSplitter) {
    if (inSplitter) {
      return IdeBundle.message("action.close.all.unpinned.editors.in.tab.group");
    }
    else {
      return IdeBundle.message("action.close.all.unpinned.editors");
    }
  }

  @Override
  protected boolean isActionEnabled(final Project project, final AnActionEvent event) {
    final ArrayList<Pair<EditorComposite,EditorWindow>> filesToClose = getFilesToClose(event);
    if (filesToClose.isEmpty()) return false;
    Set<EditorWindow> checked = new HashSet<>();
    boolean hasPinned = false;
    boolean hasUnpinned = false;
    for (Pair<EditorComposite, EditorWindow> pair : filesToClose) {
      final EditorWindow window = pair.second;
      if (checked.add(window)) {
        for (EditorWithProviderComposite e : window.getEditors()) {
          if (e.isPinned()) {
            hasPinned = true;
          }
          else {
            hasUnpinned = true;
          }
        }
        if (/*hasPinned && */hasUnpinned) {
          return true;
        }
      }
    }
    return false;
  }
}