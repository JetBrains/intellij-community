package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;

public abstract class BaseCodeInsightAction extends CodeInsightAction {
  private final boolean myLookForInjectedEditor;

  protected BaseCodeInsightAction() {
    this(true);
  }
  protected BaseCodeInsightAction(boolean lookForInjectedEditor) {
    myLookForInjectedEditor = lookForInjectedEditor;
  }
  protected Editor getEditor(final DataContext dataContext, final Project project) {
    Editor editor = super.getEditor(dataContext, project);
    if (!myLookForInjectedEditor) return editor;
    Editor injectedEditor = editor;
    if (editor != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.getDocument());
      injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguage(editor, psiFile);
    }
    return injectedEditor;
  }

  public boolean isValidForLookup(Lookup lookup, DataContext context) {
    return isValidForLookup();
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }

    final Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null){
      if (!isValidForLookup(activeLookup, dataContext)){
        presentation.setEnabled(false);
      }
      else{
        presentation.setEnabled(true);
      }
    } else {
      super.update(event);
    }
  }

  protected boolean isValidForLookup() {
    return false;
  }
}
