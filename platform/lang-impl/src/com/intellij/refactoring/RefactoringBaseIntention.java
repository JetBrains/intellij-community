package com.intellij.refactoring;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Iconable;

import javax.swing.*;

/**
 * User: anna
 * Date: 11/11/11
 */
public abstract class RefactoringBaseIntention extends PsiElementBaseIntentionAction implements Iconable, HighPriorityAction {
  public static final Icon REFACTORING_BULB = AllIcons.Actions.RefactoringBulb;

  @Override
  public Icon getIcon(int flags) {
    return REFACTORING_BULB;
  }
}
