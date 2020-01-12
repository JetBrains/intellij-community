// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PsiPackageReference extends PsiPolyVariantReferenceBase<PsiElement> implements EmptyResolveMessageProvider {
  private final PackageReferenceSet myReferenceSet;
  protected final int myIndex;

  public PsiPackageReference(PackageReferenceSet set, TextRange range, int index) {
    super(set.getElement(), range, set.isSoft());
    myReferenceSet = set;
    myIndex = index;
  }

  @NotNull
  protected Set<PsiPackage> getContext() {
    if (myIndex == 0) return myReferenceSet.getInitialContext();
    Set<PsiPackage> psiPackages = new HashSet<>();
    for (ResolveResult resolveResult : myReferenceSet.getReference(myIndex - 1).doMultiResolve()) {
      PsiElement psiElement = resolveResult.getElement();
      if (psiElement instanceof PsiPackage) {
        psiPackages.add((PsiPackage)psiElement);
      }
    }
    return psiPackages;
  }

  @Override
  public Object @NotNull [] getVariants() {
    Set<PsiPackage> subPackages = new HashSet<>();
    for (PsiPackage psiPackage : getContext()) {
      ContainerUtil.addAll(subPackages, psiPackage.getSubPackages(myReferenceSet.getResolveScope()));
    }
    return subPackages.toArray();
  }

  @NotNull
  @Override
  public String getUnresolvedMessagePattern() {
    //noinspection UnresolvedPropertyKey
    return JavaErrorBundle.message("cannot.resolve.package");
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return doMultiResolve();
  }

  protected ResolveResult @NotNull [] doMultiResolve() {
    Collection<PsiPackage> packages = new HashSet<>();
    for (PsiPackage parentPackage : getContext()) {
      packages.addAll(myReferenceSet.resolvePackageName(parentPackage, getValue()));
    }
    return PsiElementResolveResult.createResults(packages);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiPackage)) {
      throw new IncorrectOperationException("Cannot bind to " + element);
    }
    TextRange range = new TextRange(getReferenceSet().getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
    String newName = ((PsiPackage)element).getQualifiedName();
    return ElementManipulators.handleContentChange(getElement(), range, newName);
  }

  public PackageReferenceSet getReferenceSet() {
    return myReferenceSet;
  }
}