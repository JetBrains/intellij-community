// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;

public class CloseAllEditorsToTheRight extends CloseEditorsActionBase{
  @Override
  protected boolean isFileToClose(EditorComposite candidate, EditorWindow window) {
    EditorWithProviderComposite selectedEditor = window.getSelectedEditor();
    if (selectedEditor == candidate) return false;
    for (EditorWithProviderComposite composite : window.getEditors()) {
      if (composite == selectedEditor || composite == candidate) {
        return composite == selectedEditor;//candidate is located after selected tab
      }
    }
    return false;
  }

  @Override
  protected String getPresentationText(boolean inSplitter) {
    return IdeBundle.message("action.close.all.editors.to.the.right");
  }
}
