package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.ui.ListScrollingUtilEx;

class UpHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public UpHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = editor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    ListScrollingUtilEx.moveUp(lookup.getList(), 0);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    LookupImpl lookup = editor.getUserData(LookupImpl.LOOKUP_IN_EDITOR_KEY);
    return lookup != null || myOriginalHandler.isEnabled(editor, dataContext);
  }
}
