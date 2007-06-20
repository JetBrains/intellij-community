/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 21, 2006
 * Time: 5:42:54 PM
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class CustomSuppressableInspectionTool extends LocalInspectionTool {

  @Nullable
  @Deprecated
  public IntentionAction[] getSuppressActions(ProblemDescriptor context){
    return null;
  }

  @Nullable
  public IntentionAction[] getSuppressActions(PsiElement element) {
    final InspectionManager inspectionManager = InspectionManager.getInstance(element.getProject());
    return getSuppressActions(inspectionManager.createProblemDescriptor(element, "", new LocalQuickFix[0], ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
  }

  public abstract boolean isSuppressedFor(PsiElement element);
}