// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CloseAllEditorsToTheLeftAction extends CloseEditorsActionBase {

  @Override
  protected boolean isFileToClose(EditorComposite editor, EditorWindow window) {
    return false;
  }

  @Override
  protected boolean isFileToCloseInContext(DataContext dataContext, EditorComposite candidate, EditorWindow window) {
    VirtualFile contextFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    VirtualFile candidateFile = candidate.getFile();
    if (candidate.isPinned()) return false;
    if (Comparing.equal(candidateFile, contextFile)) return false;
    for (EditorWithProviderComposite composite : window.getEditors()) {
      VirtualFile cursorFile = composite.getFile();
      if (Comparing.equal(cursorFile, contextFile) || Comparing.equal(cursorFile, candidateFile)) {
        return isOKToClose(contextFile, candidateFile, cursorFile);
      }
    }
    return false;
  }

  protected boolean isOKToClose(VirtualFile contextFile, VirtualFile candidateFile, VirtualFile cursorFile) {
    return Comparing.equal(cursorFile, candidateFile);//candidate is located before the clicked one
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    int tabPlacement = UISettings.getInstance().getEditorTabPlacement();
    if (tabPlacement == SwingConstants.LEFT || tabPlacement == SwingConstants.RIGHT) {
      event.getPresentation().setText(IdeBundle.messagePointer(getAlternativeTextKey()));
    }
  }

  protected String getAlternativeTextKey() {
    return "action.close.all.editors.above";
  }

  @Override
  protected String getPresentationText(boolean inSplitter) {
    return ActionsBundle.actionText("CloseAllToTheLeft");
  }
}
