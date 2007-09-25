/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.jpa.model.xml.impl.converters;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.jpa.JpaMessages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

public class PsiPackageReference extends PsiReferenceBase<PsiElement>  implements EmptyResolveMessageProvider {

  private final PackageReferenceSet myReferenceSet;
  private final int myIndex;

  public PsiPackageReference(final PackageReferenceSet set, final TextRange range, final int index) {
    super(set.getElement(), range);
    myReferenceSet = set;
    myIndex = index;
  }

  @Nullable
  private PsiPackage getPsiPackage() {
    return myIndex == 0 ? getElement().getManager().findPackage("") : myReferenceSet.getReference(myIndex - 1).resolve();
  }

  public boolean isSoft() {
    return true;
  }

  @Nullable
  public PsiPackage resolve() {
    final PsiPackage parentPackage = getPsiPackage();
    if (parentPackage != null) {
      for (PsiPackage aPackage : parentPackage.getSubPackages()) {
        if (Comparing.equal(aPackage.getName(), getCanonicalText())) {
          return aPackage;
        }
      }
    }
    return null;
  }

  public Object[] getVariants() {
    final PsiPackage psiPackage = getPsiPackage();
    if (psiPackage == null) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final PsiPackage[] psiPackages = psiPackage.getSubPackages();
    final Object[] variants = new Object[psiPackages.length];
    System.arraycopy(psiPackages, 0, variants, 0, variants.length);
    return variants;
  }


  public String getUnresolvedMessagePattern() {
    return JpaMessages.message("cannot.resolve.package.0");
  }

}