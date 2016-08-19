/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PsiPackageReference extends PsiPolyVariantReferenceBase<PsiElement> implements EmptyResolveMessageProvider {

  private final PackageReferenceSet myReferenceSet;
  private final int myIndex;

  public PsiPackageReference(final PackageReferenceSet set, final TextRange range, final int index) {
    super(set.getElement(), range, set.isSoft());
    myReferenceSet = set;
    myIndex = index;
  }

  @NotNull
  private Set<PsiPackage> getContext() {
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
  @NotNull
  public Object[] getVariants() {
    Set<PsiPackage> subPackages = new HashSet<>();
    for (PsiPackage psiPackage : getContext()) {
         subPackages.addAll(Arrays.asList(psiPackage.getSubPackages(myReferenceSet.getResolveScope())));
    }

    return subPackages.toArray();
  }

  @NotNull
  @Override
  public String getUnresolvedMessagePattern() {
    return JavaErrorMessages.message("cannot.resolve.package");
  }

  @Override
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    return doMultiResolve();
  }

  @NotNull
  protected ResolveResult[] doMultiResolve() {
    final Collection<PsiPackage> packages = new HashSet<>();
    for (PsiPackage parentPackage : getContext()) {
      packages.addAll(myReferenceSet.resolvePackageName(parentPackage, getValue()));
    }
    return PsiElementResolveResult.createResults(packages);
  }

  @Override
  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiPackage)) {
      throw new IncorrectOperationException("Cannot bind to " + element);
    }
    final String newName = ((PsiPackage)element).getQualifiedName();
    final TextRange range =
      new TextRange(getReferenceSet().getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(getElement());
    return manipulator.handleContentChange(getElement(), range, newName);
  }

  public PackageReferenceSet getReferenceSet() {
    return myReferenceSet;
  }
}
