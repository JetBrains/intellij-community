package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;

public class ChooseItemCompleteStatementAction extends EditorAction {
  public ChooseItemCompleteStatementAction(){
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.completeStatement");

      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      lookup.finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    }
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext){
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    presentation.setEnabled(lookup != null);
  }
}