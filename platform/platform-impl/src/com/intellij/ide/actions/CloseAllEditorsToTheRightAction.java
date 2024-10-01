// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class CloseAllEditorsToTheRightAction extends CloseAllEditorsToTheLeftAction {
  @Override
  protected boolean isOKToClose(VirtualFile contextFile, VirtualFile candidateFile, VirtualFile cursorFile) {
    return Comparing.equal(cursorFile, contextFile);//candidate is located after the clicked one
  }

  @Override
  protected String getAlternativeTextKey() {
    return "action.close.all.editors.below";
  }

  @Override
  protected String getPresentationText(boolean inSplitter) {
    return ActionsBundle.actionText("CloseAllToTheRight");
  }
}
