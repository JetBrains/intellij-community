/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 21, 2006
 * Time: 5:42:54 PM
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;

public abstract class CustomSuppresableInspectionTool extends LocalInspectionTool {
  public abstract IntentionAction[] getSuppressActions(ProblemDescriptor context);

  public abstract boolean isSuppressedFor(PsiElement element);
}