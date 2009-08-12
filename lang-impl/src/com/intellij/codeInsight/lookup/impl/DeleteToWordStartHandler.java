package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class DeleteToWordStartHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public DeleteToWordStartHandler(EditorActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup != null){
      lookup.hide();
    }

    myOriginalHandler.execute(editor, dataContext);
  }
}