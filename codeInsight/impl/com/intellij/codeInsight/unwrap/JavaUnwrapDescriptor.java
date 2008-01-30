package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

public class JavaUnwrapDescriptor implements UnwrapDescriptor {
  private static final Unwrapper[] UNWRAPPERS = new Unwrapper[] {
      new JavaElseUnwrapper(),
      new JavaIfUnwrapper(),
      new JavaCatchUnwrapper(),
      new JavaTryUnwrapper()
  };

  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(PsiElement e) {
    List<Pair<PsiElement, Unwrapper>> result = new ArrayList<Pair<PsiElement, Unwrapper>>();
    while (e != null) {
      for (Unwrapper u : UNWRAPPERS) {
        if (u.isApplicableTo(e)) result.add(new Pair<PsiElement, Unwrapper>(e, u));
      }
      e = e.getParent();
    }
    return result;
  }
}
