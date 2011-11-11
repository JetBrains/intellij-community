package com.intellij.refactoring;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;

import javax.swing.*;

/**
 * User: anna
 * Date: 11/11/11
 */
public abstract class RefactoringBaseIntention extends PsiElementBaseIntentionAction implements Iconable, HighPriorityAction {
  public static final Icon REFACTORING_BULB = IconLoader.getIcon("/actions/refactoringBulb.png");

  @Override
  public Icon getIcon(int flags) {
    return REFACTORING_BULB;
  }
}
