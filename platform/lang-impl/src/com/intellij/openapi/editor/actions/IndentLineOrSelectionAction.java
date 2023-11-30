// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;


public final class IndentLineOrSelectionAction extends LangIndentSelectionAction implements ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(originalIsEnabled(editor, false));
  }

  @Override
  protected boolean wantSelection() {
    return false;
  }
}
