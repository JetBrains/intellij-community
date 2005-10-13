package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;


/**
 * @author ven
 */
public class RightSideTypeMacro implements Macro {
  public String getName() {
    return "rightSideType";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.right.side.type");
  }

  public String getDefaultValue() {
    return "A";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) element;
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) return null;
      return new PsiTypeResult(rhs.getType(), rhs.getManager());
    } else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable) element;
      PsiExpression initializer = var.getInitializer();
      if (initializer == null) return null;
      PsiType type = RefactoringUtil.getTypeByExpression(initializer);
      if (type == null) return null;
      return new PsiTypeResult(type, initializer.getManager());
    }
    return null;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    return new LookupItem[0];
  }
}
