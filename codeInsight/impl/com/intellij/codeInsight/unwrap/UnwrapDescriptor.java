package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.Pair;

import java.util.List;

public interface UnwrapDescriptor {
  List<Pair<PsiElement, Unwrapper>> collectUnwrappers(PsiElement e);
}
