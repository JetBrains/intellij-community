package com.intellij.codeInsight.completion.methodChains.completion.context;

import com.intellij.codeInsight.completion.JavaChainLookupElement;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.methodChains.completion.lookup.sub.GetterLookupSubLookupElement;
import com.intellij.codeInsight.completion.methodChains.completion.lookup.sub.SubLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ContextRelevantVariableGetter {
  private final PsiVariable myVariable;
  private final PsiMethod myMethod;

  public ContextRelevantVariableGetter(final PsiVariable variable, final PsiMethod method) {
    myVariable = variable;
    myMethod = method;
  }

  public SubLookupElement createSubLookupElement() {
    return new GetterLookupSubLookupElement(myVariable.getName(), myMethod.getName());
  }

  public LookupElement createLookupElement() {
    return new JavaChainLookupElement(new VariableLookupItem(myVariable), new JavaMethodCallElement(myMethod));
  }
}
