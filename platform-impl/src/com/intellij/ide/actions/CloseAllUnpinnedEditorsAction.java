/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;

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
  protected boolean isValidForProject(final Project project) {
    return true;
  }
}