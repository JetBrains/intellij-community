package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * @author yole
 */
public abstract class LookupActionHandler extends EditorActionHandler {
  protected final EditorActionHandler myOriginalHandler;

  public LookupActionHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public boolean executeInCommand(Editor editor, DataContext dataContext) {
    return LookupManager.getActiveLookup(editor) == null;
  }

  public void execute(Editor editor, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null){
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    lookup.markDirty();
    executeInLookup(lookup);
  }

  protected abstract void executeInLookup(LookupImpl lookup);

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    return lookup != null || myOriginalHandler.isEnabled(editor, dataContext);
  }
}
