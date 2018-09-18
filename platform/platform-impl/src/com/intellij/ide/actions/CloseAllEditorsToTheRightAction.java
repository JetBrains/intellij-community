// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseAllEditorsToTheRightAction extends CloseAllEditorsToTheLeftAction {
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
    return IdeBundle.message("action.close.all.editors.to.the.right");
  }
}
