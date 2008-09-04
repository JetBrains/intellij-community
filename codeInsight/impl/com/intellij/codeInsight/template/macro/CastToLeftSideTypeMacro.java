/*
 * @author ven
 */
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class CastToLeftSideTypeMacro implements Macro {
  public String getName() {
    return "castToLeftSideType";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.cast.to.left.side.type");
  }

  public String getDefaultValue() {
    return "(A)";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    PsiType leftType = null;
    PsiExpression rightSide = null;
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) element;
      leftType  = assignment.getLExpression().getType();
      rightSide = assignment.getRExpression();
    } else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable) element;
      leftType = var.getType();
      rightSide = var.getInitializer();
    }

    while (rightSide instanceof PsiTypeCastExpression) rightSide = ((PsiTypeCastExpression) rightSide).getOperand();

    if (leftType != null && rightSide != null && rightSide.getType() != null && !leftType.isAssignableFrom(rightSide.getType())) {
        return new TextResult("("+ leftType.getCanonicalText() + ")");
    }

    return new TextResult("");
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }
}