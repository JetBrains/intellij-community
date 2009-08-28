package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;

public class ChooseItemAction extends EditorAction {
  public ChooseItemAction(){
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    public void execute(final Editor editor, final DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    presentation.setEnabled(lookup != null);
  }
}
