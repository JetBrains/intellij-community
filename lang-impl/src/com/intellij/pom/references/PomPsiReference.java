/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.references;

import com.intellij.codeInsight.completion.CompletionData;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class PomPsiReference extends PomReference {
  private final PsiReference myDelegate;

  public PomPsiReference(PsiReference delegate) {
    super(delegate.getElement(), delegate.getRangeInElement());
    myDelegate = delegate;
  }

  @Nullable
  public PomTarget resolve() {
    final PsiElement element = myDelegate.resolve();
    return element == null ? null : PomReferenceUtil.convertPsi2Target(element);
  }

  public PsiReference getDelegate() {
    return myDelegate;
  }

  @NotNull
  @Override
  public PomTarget[] multiResolve() {
    if (myDelegate instanceof PsiPolyVariantReference) {
      final PsiPolyVariantReference reference = (PsiPolyVariantReference)myDelegate;
      final List<PomTarget> list =
        ContainerUtil.mapNotNull(reference.multiResolve(false), new NullableFunction<ResolveResult, PomTarget>() {
          public PomTarget fun(ResolveResult resolveResult) {
            final PsiElement element = resolveResult.getElement();
            if (element == null) {
              return null;
            }
            return PomReferenceUtil.convertPsi2Target(element);
          }
        });
      return list.toArray(new PomTarget[list.size()]);
    }

    final PomTarget target = resolve();
    return target == null ? PomTarget.EMPTY_ARRAY : new PomTarget[]{target};
  }

  public void bindToElement(@NotNull PomTarget target) throws IncorrectOperationException {
    myDelegate.bindToElement(((PsiTarget) target).getNavigationElement());
  }

  @Override
  public boolean isReferenceTo(@NotNull PomTarget target) {
    return myDelegate.isReferenceTo(((PsiTarget) target).getNavigationElement());
  }

  @NotNull
  @Override
  public LookupElement[] getVariants() {
    return ContainerUtil.map2Array(myDelegate.getVariants(), LookupElement.class, new Function<Object, LookupElement>() {
      public LookupElement fun(Object o) {
        return CompletionData.objectToLookupItem(o);
      }
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PomPsiReference that = (PomPsiReference)o;

    if (!myDelegate.equals(that.myDelegate)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDelegate.hashCode();
  }
}
