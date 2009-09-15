package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;

/**
 * @author peter
 */
public class PreferLessParametersWeigher extends CompletionWeigher {
  @Override
  public Integer weigh(@NotNull LookupElement element, CompletionLocation location) {
    final Object o = element.getObject();
    if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getParameterList().getParametersCount();
    }
    return 0;
  }
}
