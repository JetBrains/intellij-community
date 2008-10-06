/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PsiPackageReference extends PsiPolyVariantReferenceBase<PsiElement> implements EmptyResolveMessageProvider {

  private final PackageReferenceSet myReferenceSet;
  private final int myIndex;

  public PsiPackageReference(final PackageReferenceSet set, final TextRange range, final int index) {
    super(set.getElement(), range, set.isSoft());
    myReferenceSet = set;
    myIndex = index;
  }

  @Nullable
  private PsiPackage getContext() {
    return myIndex == 0 ? JavaPsiFacade.getInstance(getElement().getProject()).findPackage("") :
           (PsiPackage)myReferenceSet.getReference(myIndex - 1).resolve();
  }

  public Object[] getVariants() {
    final PsiPackage psiPackage = getContext();
    if (psiPackage == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final PsiPackage[] psiPackages = psiPackage.getSubPackages();
    final Object[] variants = new Object[psiPackages.length];
    System.arraycopy(psiPackages, 0, variants, 0, variants.length);
    return variants;
  }

  public String getUnresolvedMessagePattern() {
    return JavaErrorMessages.message("cannot.resolve.package");
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final PsiPackage parentPackage = getContext();
    if (parentPackage != null) {
      final Collection<PsiPackage> packages = myReferenceSet.resolvePackageName(parentPackage, getValue());
      return ContainerUtil.map2Array(packages, ResolveResult.class, new Function<PsiPackage, ResolveResult>() {
        public ResolveResult fun(final PsiPackage psiPackage) {
          return new PsiElementResolveResult(psiPackage);
        }
      });
    }
    return ResolveResult.EMPTY_ARRAY;
  }

  public PackageReferenceSet getReferenceSet() {
    return myReferenceSet;
  }
}