package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class JavaAnonymousUnwrapper extends JavaUnwrapper {
  public JavaAnonymousUnwrapper() {
    super(CodeInsightBundle.message("unwrap.anonymous"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiAnonymousClass
           && ((PsiAnonymousClass)e).getMethods().length <= 1;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return findElementToExtractFrom(e);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement from = findElementToExtractFrom(element);

    for (PsiMethod m : ((PsiAnonymousClass)element).getMethods()) {
      context.extractFromCodeBlock(m.getBody(), from);
    }

    PsiElement next = from.getNextSibling();
    if (next instanceof PsiJavaToken && ((PsiJavaToken)next).getTokenType() == JavaTokenType.SEMICOLON) {
      context.deleteExactly(from.getNextSibling());
    }
    context.deleteExactly(from);
  }

  private PsiElement findElementToExtractFrom(PsiElement el) {
    if (el.getParent() instanceof PsiNewExpression) el = el.getParent();
    el = findTopmostParentOfType(el, PsiMethodCallExpression.class);
    el = findTopmostParentOfType(el, PsiAssignmentExpression.class);
    el = findTopmostParentOfType(el, PsiDeclarationStatement.class);

    while (el.getParent() instanceof PsiExpressionStatement) {
      el = el.getParent();
    }

    return el;
  }
}