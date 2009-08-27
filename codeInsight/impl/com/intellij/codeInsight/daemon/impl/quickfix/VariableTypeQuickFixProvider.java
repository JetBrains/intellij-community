/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiType;

public class VariableTypeQuickFixProvider implements ChangeVariableTypeQuickFixProvider{
  public IntentionAction[] getFixes(PsiVariable variable, PsiType toReturn) {
    return new IntentionAction[]{new VariableTypeFix(variable, toReturn)};
  }
}