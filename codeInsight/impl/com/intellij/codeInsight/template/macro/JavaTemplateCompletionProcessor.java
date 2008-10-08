package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.List;

/**
 * @author yole
 */
public class JavaTemplateCompletionProcessor implements TemplateCompletionProcessor {
  public boolean nextTabOnItemSelected(final ExpressionContext context, final LookupElement item) {
    final List<? extends PsiElement> elements = JavaCompletionUtil.getAllPsiElements(item);
    if (elements != null) {
      if (elements.size() != 1) return false;
      if (elements.get(0) instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)elements.get(0);
        if (method.getParameterList().getParametersCount() != 0) {
          return false;
        }
      }
    }
    return true;
  }
}
