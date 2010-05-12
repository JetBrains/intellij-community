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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
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

  @NotNull
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
      return PsiElementResolveResult.createResults(packages);
    }
    return ResolveResult.EMPTY_ARRAY;
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
