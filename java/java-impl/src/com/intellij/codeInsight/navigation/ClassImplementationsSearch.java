package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;

import java.util.ArrayList;

public class ClassImplementationsSearch implements QueryExecutor<PsiElement, PsiElement> {
  public boolean execute(final PsiElement sourceElement, final Processor<PsiElement> consumer) {
    if (sourceElement instanceof PsiClass) {
      for (PsiElement implementation : getClassImplementations((PsiClass)sourceElement)) {
        if ( ! consumer.process(implementation) ) {
          return false;
        }
      }
    }
    return true;
  }

  public static PsiClass[] getClassImplementations(final PsiClass psiClass) {
    final ArrayList<PsiClass> list = new ArrayList<PsiClass>();

    ClassInheritorsSearch.search(psiClass, psiClass.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        if (!element.isInterface()) {
          list.add(element);
        }
        return true;
      }
    }));

    return list.toArray(new PsiClass[list.size()]);
  }
}
