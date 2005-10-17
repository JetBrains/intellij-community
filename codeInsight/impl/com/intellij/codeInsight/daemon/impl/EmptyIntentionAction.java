package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

/**
 * User: anna
 * Date: May 11, 2005
 */
public class EmptyIntentionAction implements IntentionAction{
  private String myName;
  private List<IntentionAction> myOptions;

  public EmptyIntentionAction(final String name, List<IntentionAction> options) {
    myName = name;
    myOptions = options;
  }

  public String getText() {
    return InspectionsBundle.message("inspection.options.action.text", myName);
  }

  public String getFamilyName() {
    return myName;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    for (IntentionAction action : myOptions) {
      if (action.isAvailable(project, editor, file)){
        return true;
      }
    }
    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
  }

  public boolean startInWriteAction() {
    return false;
  }
}
