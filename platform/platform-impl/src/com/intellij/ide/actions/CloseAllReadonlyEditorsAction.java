// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;

public class CloseAllReadonlyEditorsAction extends CloseEditorsActionBase {
  @Override
  protected boolean isFileToClose(EditorComposite editor, EditorWindow window) {
    return !editor.isPinned() && !editor.getFile().isWritable();
  }

  @Override
  protected String getPresentationText(boolean inSplitter) {
    if (inSplitter) {
      return IdeBundle.message("action.close.all.readonly.editors.in.tab.group");
    }
    else {
      return IdeBundle.message("action.close.all.readonly.editors");
    }
  }
}
