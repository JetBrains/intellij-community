// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ListIterator;

public class StaticImportResolveProcessor implements PsiScopeProcessor, NameHint {
  private final PsiImportStaticReferenceElement myReference;
  private final String myName;
  private final List<JavaResolveResult> myFieldResults = new SmartList<>();
  private final List<JavaResolveResult> myClassResult = new SmartList<>();
  private final List<JavaResolveResult> myResults = new SmartList<>();

  public StaticImportResolveProcessor(@NotNull PsiImportStaticReferenceElement reference) {
    myReference = reference;
    myName = myReference.getReferenceName();
  }

  @Override
  public boolean execute(@NotNull final PsiElement candidate, @NotNull final ResolveState state) {
    if (candidate instanceof PsiMember && ((PsiModifierListOwner)candidate).hasModifierProperty(PsiModifier.STATIC)) {
      if (candidate instanceof PsiField) {
        if (checkDomination((PsiMember)candidate, myFieldResults)) return true;
        myFieldResults.add(new OurResolveResult(candidate, myReference));
      }
      else if (candidate instanceof PsiClass) {
        if (checkDomination((PsiMember)candidate, myClassResult)) return true;
        myClassResult.add(new OurResolveResult(candidate, myReference));
      }
      else {
        myResults.add(new OurResolveResult(candidate, myReference));
      }
    }
    return true;
  }

  private static boolean checkDomination(final PsiMember candidate, final List<JavaResolveResult> results) {
    if (!results.isEmpty()) {
      for (ListIterator<JavaResolveResult> i = results.listIterator(results.size()); i.hasPrevious();) {
        final Domination domination = dominates(candidate, (PsiMember)i.previous().getElement());
        if (domination == Domination.DOMINATED_BY) {
          return true;
        }
        else if (domination == Domination.DOMINATES) {
          i.remove();
        }
      }
    }
    return false;
  }

  private static Domination dominates(final PsiMember member1, final PsiMember member2) {
    final PsiClass class1 = member1.getContainingClass();
    final PsiClass class2 = member2.getContainingClass();
    if (class1 != null && class2 != null) {
      if (class1.isInheritor(class2, true)) {
        return Domination.DOMINATES;
      }
      else if (class2.isInheritor(class1, true)) {
        return Domination.DOMINATED_BY;
      }
    }
    return Domination.EQUAL;
  }

  @Override
  public String getName(@NotNull final ResolveState state) {
    return myName;
  }

  @Override
  public <T> T getHint(@NotNull final Key<T> hintKey) {
    if (hintKey == NameHint.KEY) {
      //noinspection unchecked
      return (T)this;
    }
    return null;
  }

  @NotNull
  public JavaResolveResult[] getResults() {
    if (myResults.size() + myFieldResults.size() + myClassResult.size() > 1) {
      filterInvalid(myResults);
      filterInvalid(myFieldResults);
      filterInvalid(myClassResult);
    }
    if (!myFieldResults.isEmpty()) {
      myResults.addAll(myFieldResults);
    }
    if (!myClassResult.isEmpty()) {
      myResults.addAll(myClassResult);
    }
    return myResults.toArray(new JavaResolveResult[myResults.size()]);
  }

  private static void filterInvalid(final List<JavaResolveResult> resultList) {
    if (resultList.isEmpty()) return;
    for (ListIterator<JavaResolveResult> i = resultList.listIterator(resultList.size()); i.hasPrevious();) {
      if (!i.previous().isValidResult()) i.remove();
    }
  }

  private static class OurResolveResult extends CandidateInfo {
    private final PsiImportStaticReferenceElement myReference;

    public OurResolveResult(@NotNull PsiElement candidate, final PsiImportStaticReferenceElement reference) {
      super(candidate, PsiSubstitutor.EMPTY);
      myReference = reference;
    }

    @Override
    public boolean isAccessible() {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myReference.getProject()).getResolveHelper();
      final PsiElement element = getElement();
      return element instanceof PsiMember && resolveHelper.isAccessible((PsiMember)element, myReference, null);
    }

    @Override
    public boolean isStaticsScopeCorrect() {
      return true;
    }
  }
}
