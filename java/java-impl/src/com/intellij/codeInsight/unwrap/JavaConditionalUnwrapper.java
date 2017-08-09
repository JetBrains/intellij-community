package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class JavaConditionalUnwrapper extends JavaUnwrapper {
  public JavaConditionalUnwrapper() {
    super(CodeInsightBundle.message("unwrap.conditional"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e.getParent() instanceof PsiConditionalExpression;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent();
  }
  
  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiConditionalExpression cond = (PsiConditionalExpression)element.getParent();

    PsiElement savedBlock;
    
    if (cond.getElseExpression() == element) {
      savedBlock = element;
    }
    else {
      savedBlock = cond.getThenExpression();
    }

    context.extractElement(savedBlock, cond);

    if (cond.getParent() instanceof PsiExpressionList) {
      context.delete(cond);
    }
    else {
      context.deleteExactly(cond);
    }
  }
}
