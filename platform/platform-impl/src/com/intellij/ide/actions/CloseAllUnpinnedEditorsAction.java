// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


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
        for (EditorComposite composite : window.getAllComposites()) {
          if (composite.isPinned()) {
            hasPinned = true;
          }
          else {
            hasUnpinned = true;
          }
        }
        if ((hasPinned || !event.isFromContextMenu()) && hasUnpinned) {
          return true;
        }
      }
    }
    return false;
  }
}