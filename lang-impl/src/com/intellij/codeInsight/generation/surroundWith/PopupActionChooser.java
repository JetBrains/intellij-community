package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.ide.DataManager;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;

public class PopupActionChooser {
  private final String myTitle;
  private boolean hasEnabledSurrounders;

  public PopupActionChooser(String title) {
    myTitle = title;
  }

  public void invoke(final Project project, final Editor editor, final Surrounder[] surrounders, final PsiElement[] elements){
    final DefaultActionGroup applicable = new DefaultActionGroup();
    hasEnabledSurrounders = false;

    for (Surrounder surrounder : surrounders) {
      if (surrounder.isApplicable(elements)) {
        applicable.add(new InvokeSurrounderAction(surrounder, project, editor, elements));
        hasEnabledSurrounders = true;
      }
    }

    if (hasEnabledSurrounders) {
      DataContext context = DataManager.getInstance().getDataContext(editor.getContentComponent());
      final ListPopup popup = JBPopupFactory.getInstance().
        createActionGroupPopup(myTitle,
                               applicable,
                               context,
                               JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING,
                               false);
      popup.showInBestPositionFor(editor);
    }
  }

  private static class InvokeSurrounderAction extends AnAction {
    private Surrounder mySurrounder;
    private Project myProject;
    private Editor myEditor;
    private PsiElement[] myElements;

    public InvokeSurrounderAction(Surrounder surrounder, Project project, Editor editor, PsiElement[] elements) {
      super(surrounder.getTemplateDescription());
      mySurrounder = surrounder;
      myProject = project;
      myEditor = editor;
      myElements = elements;
    }

    public void actionPerformed(AnActionEvent e) {
      CommandProcessor.getInstance().executeCommand(
          myProject, new Runnable(){
          public void run(){
            final Runnable action = new Runnable(){
              public void run(){
                SurroundWithHandler.doSurround(myProject, myEditor, mySurrounder, myElements);
              }
            };
            ApplicationManager.getApplication().runWriteAction(action);
          }
        },
        null,
        null
      );
    }
  }

  public boolean isHasEnabledSurrounders() {
    return hasEnabledSurrounders;
  }
}