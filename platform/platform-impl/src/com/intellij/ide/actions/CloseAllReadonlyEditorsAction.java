// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import org.jetbrains.annotations.NotNull;

final class CloseAllReadonlyEditorsAction extends CloseEditorsActionBase {
  @Override
  protected boolean isFileToClose(@NotNull EditorComposite editor, @NotNull EditorWindow window, @NotNull FileEditorManagerEx fileEditorManager) {
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
